package com.example.cast.persistence;

import com.example.cast.global.code.GlobalErrorCode;
import com.example.cast.global.exception.BusinessException;
import com.example.cast.integration.ai.config.AiDispatchProtectionProperties;
import com.example.cast.integration.ai.dto.*;
import com.example.cast.integration.ai.service.AiProgressService;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.annotation.Value;
import org.junit.jupiter.api.BeforeEach;
import tools.jackson.databind.ObjectMapper;
import java.util.concurrent.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
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
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AiProgressIntegrationTest extends TestDatabaseSupport {
    private static final Instant CUTOVER = Instant.parse("2020-01-01T00:00:00Z");
    private static final UUID OTHER_JOB_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired private AiJobService jobService;
    @Autowired private AiResultService resultService;
    @Autowired private EntityManager em;
    @Autowired private PlatformTransactionManager transactions;
    @Autowired private JdbcTemplate jdbc;

    // 설정만 시나리오별로 교체한다. 서비스, 트랜잭션, PostgreSQL은 실제 구현을 사용한다.
    @MockitoBean private AiDispatchProtectionProperties protection;

    @Autowired private AiProgressService progress;
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;

    @Value("${cast.ai.callback-token}")
    private String callbackToken;

    private record Job(Long taskId, UUID jobId) {}

    @BeforeEach
    void configureDefaultGate() {
        configure(CUTOVER, List.of(OTHER_JOB_ID));
    }


    private AiProgressRequest request(
            Job job, int step, String summary) {
        return new AiProgressRequest(
                job.jobId(), (short) step, summary);
    }

    private AiProgressReceipt advance(
            Job job, int step, String summary) {
        return progress.accept(
                job.taskId(), request(job, step, summary));
    }

    private Integer storedStep(Job job) {
        return jdbc.queryForObject(
                "SELECT current_step FROM backend.research_task WHERE task_id = ?",
                Integer.class, job.taskId());
    }

    private String storedSummary(Job job) {
        return jdbc.queryForObject(
                "SELECT preview_summary FROM backend.research_task WHERE task_id = ?",
                String.class, job.taskId());
    }

    private String url(Job job) {
        return "/internal/ai/research-tasks/"
                + job.taskId() + "/progress";
    }

    private void assertProgressBlocked(
            Job job, AiProgressRequest request) {
        String before = snapshot(job);

        var error = assertThrows(
                BusinessException.class,
                () -> progress.accept(job.taskId(), request));

        assertEquals(
                GlobalErrorCode.DATA_CONFLICT,
                error.getErrorCode());

        assertEquals(before, snapshot(job));
    }

    @Test
    void fullProgressLifecycleEndsAtSix() {
        Job job = createJob();

        assertEquals(1, storedStep(job));

        for (int step = 2; step <= 5; step++) {
            var receipt = advance(job, step, "draft " + step);

            assertEquals(job.taskId(), receipt.taskId());
            assertEquals(ResearchTaskStatus.RUNNING, receipt.status());
            assertEquals((short) step, receipt.currentStep());
            assertEquals(step, storedStep(job));
            assertEquals("draft " + step, storedSummary(job));
        }

        resultService.accept(job.taskId(), success(job.jobId()));

        assertEquals(6, storedStep(job));
        assertEquals(1L, answerCount(job));
    }

    @Test
    void progressDoesNotRequireDispatchedAt() {
        Job job = createJob();

        assertNull(jdbc.queryForObject(
                "SELECT dispatched_at FROM backend.ai_job WHERE job_id = ?",
                java.sql.Timestamp.class, job.jobId()));

        advance(job, 2, null);

        assertEquals(2, storedStep(job));
    }

    @Test
    void omittedOrBlankSummaryPreservesExistingDraft() {
        Job job = createJob();

        advance(job, 3, "first draft");
        advance(job, 4, null);
        assertEquals("first draft", storedSummary(job));

        advance(job, 5, "   ");
        assertEquals("first draft", storedSummary(job));
    }

    @Test
    void sameStepCanUpdateSummary() {
        Job job = createJob();

        advance(job, 4, "first draft");
        advance(job, 4, "  revised draft  ");

        assertEquals(4, storedStep(job));
        assertEquals("revised draft", storedSummary(job));
    }

    @Test
    void duplicateProgressDoesNotChangeAnyRows() {
        Job job = createJob();

        var first = advance(job, 4, "draft");
        String before = snapshot(job);

        assertEquals(first, advance(job, 4, "draft"));
        assertEquals(before, snapshot(job));

        assertEquals(first, advance(job, 4, null));
        assertEquals(before, snapshot(job));
    }

    @Test
    void skippingStagesIsAllowedButRegressionIsRejected() {
        Job job = createJob();

        advance(job, 5, "draft");

        assertEquals(5, storedStep(job));

        assertProgressBlocked(job, request(job, 3, "old draft"));
    }

    @Test
    void wrongJobIdIsRejectedWithoutWrites() {
        Job job = createJob();

        assertProgressBlocked(job,
                new AiProgressRequest(
                        UUID.randomUUID(), (short) 3, "draft"));
    }

    @Test
    void jobFromAnotherTaskIsRejectedWithoutWrites() {
        Job first = createJob();
        Job second = createJob();

        String firstBefore = snapshot(first);

        assertProgressBlocked(second, request(first, 3, "draft"));

        assertEquals(firstBefore, snapshot(first));
    }

    @Test
    void twelveProtectedJobsRemainUnchanged() {
        var protectedJobs = new ArrayList<Job>();

        for (int i = 0; i < 12; i++) {
            protectedJobs.add(createJob());
        }

        configure(CUTOVER,
                protectedJobs.stream().map(Job::jobId).toList());

        for (Job job : protectedJobs) {
            assertProgressBlocked(job, request(job, 4, "draft"));
        }
    }

    @Test
    void beforeAndExactlyAtCutoverAreRejected() {
        for (Instant time : List.of(
                CUTOVER.minusSeconds(1), CUTOVER)) {

            Job job = createJob();
            setCreatedAt(job, time);

            assertProgressBlocked(job, request(job, 3, "draft"));
        }
    }

    @Test
    void afterCutoverIsAllowed() {
        Job job = createJob();

        setCreatedAt(job, CUTOVER.plusSeconds(1));
        advance(job, 2, null);

        assertEquals(2, storedStep(job));
    }

    @Test
    void invalidProtectionSettingsFailClosed() {
        Job job = createJob();
        String before = snapshot(job);

        var settings = List.of(
                new AiDispatchProtectionProperties(null, null),
                new AiDispatchProtectionProperties(
                        "invalid-cutover", OTHER_JOB_ID.toString()),
                new AiDispatchProtectionProperties(
                        CUTOVER.toString(), "invalid-uuid"),
                new AiDispatchProtectionProperties(
                        CUTOVER.toString(), "")
        );

        for (var setting : settings) {
            configure(setting);

            assertThrows(IllegalStateException.class,
                    () -> advance(job, 3, "draft"));

            assertEquals(before, snapshot(job));
        }
    }

    @Test
    void completedTaskRejectsLateProgress() {
        Job job = createJob();

        advance(job, 5, "draft");
        resultService.accept(job.taskId(), success(job.jobId()));

        assertProgressBlocked(job, request(job, 5, "late draft"));
        assertEquals(6, storedStep(job));
    }

    @Test
    void failedTaskRejectsLateProgress() {
        Job job = createJob();

        advance(job, 3, "draft");
        resultService.accept(job.taskId(), failure(job.jobId()));

        assertProgressBlocked(job, request(job, 4, "late draft"));
        assertEquals(3, storedStep(job));
        assertEquals(0L, answerCount(job));
    }

    @Test
    void canceledTaskRejectsLateProgress() {
        Job job = createJob();

        advance(job, 3, "draft");

        new TransactionTemplate(transactions)
                .executeWithoutResult(tx ->
                        em.find(ResearchTask.class, job.taskId())
                                .markCanceled());

        assertProgressBlocked(job, request(job, 4, "late draft"));
        assertEquals(3, storedStep(job));
    }

    @Test
    void authenticatedHttpRequestPersistsProgress() throws Exception {
        Job job = createJob();

        mvc.perform(post(url(job))
                        .header("X-AI-Service-Token", callbackToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(
                                request(job, 4, "HTTP draft"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.taskId").value(job.taskId()))
                .andExpect(jsonPath("$.result.status").value("RUNNING"))
                .andExpect(jsonPath("$.result.currentStep").value(4))
                .andExpect(jsonPath("$.result.previewSummary")
                        .value("HTTP draft"));

        assertEquals(4, storedStep(job));
        assertEquals("HTTP draft", storedSummary(job));
    }

    @Test
    void missingOrWrongTokenCannotModifyDatabase() throws Exception {
        Job job = createJob();
        String before = snapshot(job);

        String body = mapper.writeValueAsString(
                request(job, 3, "draft"));

        mvc.perform(post(url(job))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());

        mvc.perform(post(url(job))
                        .header("X-AI-Service-Token", "wrong")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());

        assertEquals(before, snapshot(job));
    }

    @Test
    void userJwtCannotAccessInternalProgress() throws Exception {
        Job job = createJob();
        String before = snapshot(job);

        var signup = mapper.createObjectNode()
                .put("email", UUID.randomUUID() + "@example.com")
                .put("password", "TestPassword123!");

        var response = mvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(signup)))
                .andExpect(status().isCreated())
                .andReturn();

        String jwt = mapper.readTree(
                response.getResponse().getContentAsByteArray())
                .path("result").path("accessToken").asString();

        assertFalse(jwt.isBlank());

        mvc.perform(post(url(job))
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(
                                request(job, 3, "draft"))))
                .andExpect(status().isUnauthorized());

        assertEquals(before, snapshot(job));
    }

    @Test
    void invalidHttpPayloadsReturn400WithoutWrites() throws Exception {
        Job job = createJob();
        String before = snapshot(job);

        var bodies = new ArrayList<String>();

        for (int step : List.of(0, 1, 6, 7)) {
            bodies.add(mapper.writeValueAsString(
                    request(job, step, "draft")));
        }

        bodies.add("{}");

        bodies.add(mapper.writeValueAsString(
                new AiProgressRequest(null, (short) 3, null)));

        bodies.add(mapper.writeValueAsString(
                new AiProgressRequest(job.jobId(), null, null)));

        bodies.add(mapper.writeValueAsString(
                request(job, 3, "a".repeat(351))));

        bodies.add("""
                {"jobId":"not-a-uuid","currentStep":3}
                """);

        for (String body : bodies) {
            mvc.perform(post(url(job))
                            .header("X-AI-Service-Token", callbackToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());

            assertEquals(before, snapshot(job));
        }
    }

    @Test
    void maximumLengthSummaryIsAccepted() throws Exception {
        Job job = createJob();
        String summary = "a".repeat(350);

        mvc.perform(post(url(job))
                        .header("X-AI-Service-Token", callbackToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(
                                request(job, 5, summary))))
                .andExpect(status().isOk());

        assertEquals(summary, storedSummary(job));
    }

    @Test
    void regressiveHttpRequestReturns409() throws Exception {
        Job job = createJob();

        advance(job, 5, "draft");
        String before = snapshot(job);

        mvc.perform(post(url(job))
                        .header("X-AI-Service-Token", callbackToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(
                                request(job, 3, "old draft"))))
                .andExpect(status().isConflict());

        assertEquals(before, snapshot(job));
    }

    @Test
    void concurrentProgressAndFinalResultEndAtSix() throws Exception {
        Job job = createJob();
        var start = new CountDownLatch(1);

        try (var pool = Executors.newFixedThreadPool(2)) {
            var progressFuture = pool.submit(() -> {
                assertTrue(start.await(10, TimeUnit.SECONDS));

                try {
                    advance(job, 5, "draft");
                    return true;
                } catch (BusinessException error) {
                    assertEquals(GlobalErrorCode.DATA_CONFLICT,
                            error.getErrorCode());
                    return false;
                }
            });

            var resultFuture = pool.submit(() -> {
                assertTrue(start.await(10, TimeUnit.SECONDS));
                return resultService.accept(
                        job.taskId(), success(job.jobId()));
            });

            start.countDown();

            progressFuture.get(20, TimeUnit.SECONDS);

            assertEquals(
                    ResearchTaskStatus.COMPLETED,
                    resultFuture.get(20, TimeUnit.SECONDS).status());
        }

        assertEquals(6, storedStep(job));
        assertEquals(1L, answerCount(job));

        assertProgressBlocked(job, request(job, 5, "late draft"));
    }


    @Test
    void runningTaskWithAcceptedResultRejectsProgress() {
        Job job = createJob();

        advance(job, 3, "original draft");

        assertEquals(1, jdbc.update("""
                UPDATE backend.ai_job
                SET result_hash = ?, outcome = 'SUCCESS'
                WHERE job_id = ?
                """, "a".repeat(64), job.jobId()));

        assertEquals("RUNNING", jdbc.queryForObject("""
                SELECT status
                FROM backend.research_task
                WHERE task_id = ?
                """, String.class, job.taskId()));

        assertEquals(0L, answerCount(job));

        assertProgressBlocked(
                job, request(job, 4, "new draft"));

        assertEquals(3, storedStep(job));
        assertEquals("original draft", storedSummary(job));
    }

    @Test
    void runningTaskWithExistingAnswerRejectsProgress() {
        Job job = createJob();

        advance(job, 3, "original draft");

        new TransactionTemplate(transactions)
                .executeWithoutResult(tx -> {
                    var task = em.find(
                            ResearchTask.class, job.taskId());

                    em.persist(
                            new com.example.cast.research.entity.Answer(task));
                });

        assertEquals("RUNNING", jdbc.queryForObject("""
                SELECT status
                FROM backend.research_task
                WHERE task_id = ?
                """, String.class, job.taskId()));

        assertNull(jdbc.queryForObject("""
                SELECT result_hash
                FROM backend.ai_job
                WHERE job_id = ?
                """, String.class, job.jobId()));

        assertEquals(1L, answerCount(job));

        String answerBefore = jdbc.queryForObject("""
                SELECT to_jsonb(a)::text
                FROM backend.answer a
                WHERE task_id = ?
                """, String.class, job.taskId());

        assertProgressBlocked(
                job, request(job, 4, "new draft"));

        String answerAfter = jdbc.queryForObject("""
                SELECT to_jsonb(a)::text
                FROM backend.answer a
                WHERE task_id = ?
                """, String.class, job.taskId());

        assertEquals(answerBefore, answerAfter);
        assertEquals(1L, answerCount(job));
        assertEquals(3, storedStep(job));
        assertEquals("original draft", storedSummary(job));
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
