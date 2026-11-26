package com.example.cast.persistence;

import com.example.cast.global.code.GlobalErrorCode;
import com.example.cast.global.exception.BusinessException;
import com.example.cast.integration.ai.config.AiDispatchProtectionProperties;
import com.example.cast.integration.ai.dto.AiResultRequest;
import com.example.cast.integration.ai.service.AiJobService;
import com.example.cast.integration.ai.service.AiResultService;
import com.example.cast.research.entity.ResearchQuery;
import com.example.cast.research.entity.ResearchTask;
import com.example.cast.research.entity.ResearchTaskStatus;
import com.example.cast.user.entity.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doAnswer;

/** 격리 PostgreSQL에서 AI 콜백 보호 및 데이터 불변성을 검증한다. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class AiCallbackProtectionIntegrationTest extends TestDatabaseSupport {
    private static final Instant CUTOVER = Instant.parse("2020-01-01T00:00:00Z");
    private static final UUID OTHER_JOB_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired private AiJobService jobService;
    @Autowired private AiResultService resultService;
    @Autowired private EntityManager em;
    @Autowired private PlatformTransactionManager transactions;
    @Autowired private JdbcTemplate jdbc;

    // 설정만 시나리오별로 교체한다. 서비스, 트랜잭션, PostgreSQL은 실제 구현을 사용한다.
    @MockitoBean private AiDispatchProtectionProperties protection;

    private record Job(Long taskId, UUID jobId) {}

    @Test
    void twelveProtectedJobsRejectSuccessCallbacksWithoutAnyWrites() {
        var protectedJobs = new ArrayList<Job>();
        for (int i = 0; i < 12; i++) {
            protectedJobs.add(createJob());
        }
        configure(CUTOVER, protectedJobs.stream().map(Job::jobId).toList());

        for (Job job : protectedJobs) {
            assertBlockedWithoutWrites(job, success(job.jobId()));
        }
    }

    @Test
    void protectedJobRejectsFailureCallbackWithoutAnyWrites() {
        Job job = createJob();
        configure(CUTOVER, List.of(job.jobId()));
        assertBlockedWithoutWrites(job, failure(job.jobId()));
    }

    @Test
    void jobCreatedBeforeCutoverIsRejectedEvenWhenUuidIsNotProtected() {
        Job job = createJob();
        setCreatedAt(job, CUTOVER.minusSeconds(1));
        configure(CUTOVER, List.of(OTHER_JOB_ID));
        assertBlockedWithoutWrites(job, success(job.jobId()));
    }

    @Test
    void jobCreatedExactlyAtCutoverIsRejected() {
        Job job = createJob();
        setCreatedAt(job, CUTOVER);
        configure(CUTOVER, List.of(OTHER_JOB_ID));
        assertBlockedWithoutWrites(job, failure(job.jobId()));
    }

    @Test
    void missingOrMalformedSettingsAlwaysFailClosedEvenWithDispatcherDisabled() {
        Job job = createJob();
        String before = snapshot(job);

        var invalidSettings = List.of(
                new AiDispatchProtectionProperties(null, null),
                new AiDispatchProtectionProperties("invalid-cutover", OTHER_JOB_ID.toString()),
                new AiDispatchProtectionProperties(CUTOVER.toString(), "not-a-uuid"),
                new AiDispatchProtectionProperties(CUTOVER.toString(), "")
        );

        for (var settings : invalidSettings) {
            configure(settings);
            assertThrows(IllegalStateException.class,
                    () -> resultService.accept(job.taskId(), success(job.jobId())));
            assertEquals(before, snapshot(job), "설정 오류에도 Job/Task/Answer가 변경되면 안 됨");
        }
    }

    @Test
    void eligibleSuccessCallbackAndIdenticalRetryRemainIdempotent() {
        Job job = createJob();
        configure(CUTOVER, List.of(OTHER_JOB_ID));
        var callback = success(job.jobId());

        var first = resultService.accept(job.taskId(), callback);
        assertEquals(ResearchTaskStatus.COMPLETED, first.status());
        assertNotNull(first.answerId());
        String afterFirst = snapshot(job);

        assertEquals(first, resultService.accept(job.taskId(), callback));
        assertEquals(afterFirst, snapshot(job), "동일 콜백 재전송에서 행이 다시 변경되면 안 됨");
        assertEquals(1L, answerCount(job));
    }

    @Test
    void eligibleFailureCallbackAndIdenticalRetryRemainIdempotent() {
        Job job = createJob();
        configure(CUTOVER, List.of(OTHER_JOB_ID));
        var callback = failure(job.jobId());

        var first = resultService.accept(job.taskId(), callback);
        assertEquals(ResearchTaskStatus.FAILED, first.status());
        assertNull(first.answerId());
        String afterFirst = snapshot(job);

        assertEquals(first, resultService.accept(job.taskId(), callback));
        assertEquals(afterFirst, snapshot(job));
        assertEquals(0L, answerCount(job));
    }

    @Test
    void alreadyAcceptedJobIsStillRejectedIfItBecomesProtected() {
        Job job = createJob();
        var callback = success(job.jobId());
        configure(CUTOVER, List.of(OTHER_JOB_ID));
        resultService.accept(job.taskId(), callback);
        String beforeRetry = snapshot(job);

        configure(CUTOVER, List.of(job.jobId()));
        assertBlockedWithoutWrites(job, callback);
        assertEquals(beforeRetry, snapshot(job));
        assertEquals(1L, answerCount(job));
    }

    private Job createJob() {
        Long taskId = new TransactionTemplate(transactions).execute(status -> {
            var user = new User(UUID.randomUUID() + "@example.invalid", "test-password-hash");
            em.persist(user);
            var query = new ResearchQuery(user, "callback protection test question");
            em.persist(query);
            var task = new ResearchTask(query);
            em.persist(task);
            return task.getTaskId();
        });
        assertNotNull(taskId);
        return new Job(taskId, jobService.enqueue(taskId).jobId());
    }

    private void setCreatedAt(Job job, Instant time) {
        assertEquals(1, jdbc.update(
                "UPDATE backend.ai_job SET created_at = ? WHERE job_id = ?",
                Timestamp.from(time), job.jobId()));
    }

    private void configure(Instant cutover, List<UUID> protectedIds) {
        String ids = protectedIds.stream().map(UUID::toString).collect(Collectors.joining(","));
        configure(new AiDispatchProtectionProperties(cutover.toString(), ids));
    }

    private void configure(AiDispatchProtectionProperties settings) {
        // 실제 requiredGate()의 파싱/검증 동작을 사용하고, Spring 설정 빈만 대체한다.
        doAnswer(invocation -> settings.requiredGate()).when(protection).requiredGate();
    }

    private void assertBlockedWithoutWrites(Job job, AiResultRequest request) {
        String before = snapshot(job);
        var error = assertThrows(BusinessException.class,
                () -> resultService.accept(job.taskId(), request));
        assertEquals(GlobalErrorCode.DATA_CONFLICT, error.getErrorCode());
        assertEquals(before, snapshot(job), "보호된 콜백은 DB 행을 변경하면 안 됨");
    }

    private String snapshot(Job job) {
        return jdbc.queryForObject("""
                SELECT jsonb_build_object(
                    'job', to_jsonb(j),
                    'task', to_jsonb(t),
                    'answer_count', (SELECT count(*) FROM backend.answer a WHERE a.task_id = t.task_id)
                )::text
                FROM backend.ai_job j
                JOIN backend.research_task t ON t.task_id = j.task_id
                WHERE t.task_id = ?
                """, String.class, job.taskId());
    }

    private Long answerCount(Job job) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM backend.answer WHERE task_id = ?",
                Long.class, job.taskId());
    }

    private static AiResultRequest success(UUID jobId) {
        return new AiResultRequest(jobId, AiResultRequest.Outcome.SUCCESS,
                "test answer", "test summary", null, List.of());
    }

    private static AiResultRequest failure(UUID jobId) {
        return new AiResultRequest(jobId, AiResultRequest.Outcome.FAILURE,
                null, null, "test-only AI failure", List.of());
    }
}
