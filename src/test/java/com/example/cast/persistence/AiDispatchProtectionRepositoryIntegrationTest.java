package com.example.cast.persistence;

import com.example.cast.integration.ai.repository.AiJobRepository;
import com.example.cast.integration.ai.service.AiJobService;
import com.example.cast.research.entity.ResearchQuery;
import com.example.cast.research.entity.ResearchTask;
import com.example.cast.user.entity.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** 실제 PostgreSQL에서 전환 시각/보호 UUID 조건이 페이징 이전에 적용되는지 검증한다. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class AiDispatchProtectionRepositoryIntegrationTest extends TestDatabaseSupport {
    private static final Instant CUTOVER = Instant.parse("2026-12-01T00:00:00Z");

    @Autowired private AiJobRepository repository;
    @Autowired private AiJobService jobService;
    @Autowired private EntityManager em;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactions;

    private record Job(UUID jobId, long taskId, Instant createdAt) {}

    private record Fixture(List<UUID> protectedIds, List<UUID> eligibleIds,
                           UUID oldUnprotected, UUID atCutover, UUID dispatched,
                           UUID completed, UUID canceled) {}

    @Test
    void postgresFiltersLegacyProtectedAndIneligibleJobsBeforePageLimitWithoutWrites() throws Exception {
        Fixture fixture = new TransactionTemplate(transactions).execute(status -> seedJobs());
        assertNotNull(fixture);

        Map<UUID, String> before = snapshotAllJobs();
        assertEquals(43, before.size(), "격리된 테스트 DB에 예상한 43개 Job이 있어야 함");
        assertEquals(13, fixture.protectedIds().size(), "기존 12건과 신규 보호 대상 1건");
        assertEquals(25, fixture.eligibleIds().size());

        var firstPage = repository.pendingEligible(CUTOVER, fixture.protectedIds(), PageRequest.of(0, 20));
        var secondPage = repository.pendingEligible(CUTOVER, fixture.protectedIds(), PageRequest.of(1, 20));
        var thirdPage = repository.pendingEligible(CUTOVER, fixture.protectedIds(), PageRequest.of(2, 20));

        assertEquals(fixture.eligibleIds().subList(0, 20), ids(firstPage),
                "기존 12건이 조회 제한 20건을 차지하면 안 됨");
        assertEquals(fixture.eligibleIds().subList(20, 25), ids(secondPage));
        assertTrue(thirdPage.isEmpty());

        var observed = new ArrayList<UUID>(ids(firstPage));
        observed.addAll(ids(secondPage));
        assertEquals(25, observed.stream().distinct().count(), "신규 25건은 중복 없이 전부 조회되어야 함");
        assertTrue(java.util.Collections.disjoint(observed, fixture.protectedIds()));
        assertFalse(observed.contains(fixture.oldUnprotected()), "전환 전 작업은 보호 목록에 없어도 제외");
        assertFalse(observed.contains(fixture.atCutover()), "전환 시각과 동일한 Job도 제외(엄격한 > 비교)");
        assertFalse(observed.contains(fixture.dispatched()), "이미 전송한 Job 제외");
        assertFalse(observed.contains(fixture.completed()), "결과를 수락한 Job 제외");
        assertFalse(observed.contains(fixture.canceled()), "RUNNING이 아닌 Task 제외");

        assertEquals(before, snapshotAllJobs(),
                "조회 전후 모든 Job 및 연결 Task의 SHA-256이 같아야 함");
    }

    /** 모두 동일한 격리 DB 트랜잭션에서 생성한다. AWS나 개발 DB에 접근하지 않는다. */
    private Fixture seedJobs() {
        var user = new User(UUID.randomUUID() + "@example.invalid", "test-only-password-hash");
        em.persist(user);
        var stamped = new ArrayList<Job>();
        var legacy = new ArrayList<UUID>();
        var eligible = new ArrayList<UUID>();

        for (int i = 0; i < 12; i++) {
            legacy.add(addJob(user, "legacy-" + i, CUTOVER.minusSeconds(3600 + i), stamped).jobId());
        }
        var oldUnprotected = addJob(user, "old-unprotected", CUTOVER.minusSeconds(60), stamped);
        var atCutover = addJob(user, "exact-cutover", CUTOVER, stamped);
        var protectedNew = addJob(user, "new-but-protected", CUTOVER.plusSeconds(1), stamped);
        for (int i = 0; i < 25; i++) {
            eligible.add(addJob(user, "eligible-" + i, CUTOVER.plusSeconds(10 + i), stamped).jobId());
        }
        var dispatched = addJob(user, "already-dispatched", CUTOVER.plusSeconds(50), stamped);
        var completed = addJob(user, "result-already-accepted", CUTOVER.plusSeconds(51), stamped);
        var canceled = addJob(user, "canceled-task", CUTOVER.plusSeconds(52), stamped);
        em.find(ResearchTask.class, canceled.taskId()).markCanceled();

        em.flush();
        for (Job job : stamped) {
            int changed = jdbc.update("UPDATE backend.ai_job SET created_at = ?, updated_at = ? WHERE job_id = ?",
                    Timestamp.from(job.createdAt()), Timestamp.from(job.createdAt()), job.jobId());
            assertEquals(1, changed);
        }
        jdbc.update("UPDATE backend.ai_job SET dispatched_at = ? WHERE job_id = ?",
                Timestamp.from(CUTOVER.plusSeconds(55)), dispatched.jobId());
        jdbc.update("UPDATE backend.ai_job SET result_hash = ?, outcome = 'SUCCESS' WHERE job_id = ?",
                "a".repeat(64), completed.jobId());
        em.clear();

        var protectedIds = new ArrayList<>(legacy);
        protectedIds.add(protectedNew.jobId());
        return new Fixture(List.copyOf(protectedIds), List.copyOf(eligible),
                oldUnprotected.jobId(), atCutover.jobId(), dispatched.jobId(),
                completed.jobId(), canceled.jobId());
    }

    private Job addJob(User user, String question, Instant createdAt, List<Job> stamped) {
        var query = new ResearchQuery(user, question);
        em.persist(query);
        var task = new ResearchTask(query);
        em.persist(task);
        var request = jobService.enqueue(task.getTaskId());
        var job = new Job(request.jobId(), task.getTaskId(), createdAt);
        stamped.add(job);
        return job;
    }

    private static List<UUID> ids(List<com.example.cast.integration.ai.entity.AiJob> jobs) {
        return jobs.stream().map(com.example.cast.integration.ai.entity.AiJob::getJobId).toList();
    }

    /** Job/Task 전체 행 내용을 해시한다. 조회 작업의 부수 효과 여부도 검증한다. */
    private Map<UUID, String> snapshotAllJobs() throws NoSuchAlgorithmException {
        var digest = MessageDigest.getInstance("SHA-256");
        return jdbc.query("SELECT j.job_id, to_jsonb(j)::text AS job_json, "
                + "to_jsonb(t)::text AS task_json FROM backend.ai_job j "
                + "JOIN backend.research_task t ON t.task_id = j.task_id ORDER BY j.job_id", rs -> {
            Map<UUID, String> result = new LinkedHashMap<>();
            while (rs.next()) {
                UUID id = (UUID) rs.getObject("job_id");
                String data = rs.getString("job_json") + "\n" + rs.getString("task_json");
                result.put(id, HexFormat.of().formatHex(digest.digest(data.getBytes(StandardCharsets.UTF_8))));
            }
            return result;
        });
    }
}
