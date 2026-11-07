package com.example.cast.persistence;

import com.example.cast.user.entity.*;
import com.example.cast.research.entity.*;
import com.example.cast.claim.entity.*;
import com.example.cast.retrieval.entity.*;
import com.example.cast.document.entity.*;
import com.example.cast.source.entity.*;
import com.example.cast.evaluation.entity.*;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** 기존 영속성/제약 검증을 임시 DB에서 실행하며 모든 Flyway 버전을 적용한다. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Transactional
class CastPersistenceIntegrationTest extends TestDatabaseSupport {

    @PersistenceContext
    private EntityManager em;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void verifyTargetDatabaseBeforeEachTest() throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(
                     "SELECT current_database(), current_user, current_schema()")) {
            assertEquals(
                    postgres.getJdbcUrl(),
                    connection.getMetaData().getURL(),
                    "Refuse any database other than the disposable test target"
            );
            assertTrue(result.next());
            assertEquals("cast_persistence_test", result.getString(1));
            assertEquals("cast_persistence", result.getString(2));
            assertEquals("public", result.getString(3));
        }
    }

    private String uniqueEmail() {
        return "cast-" + UUID.randomUUID() + "@example.invalid";
    }

    private User newUser() {
        User user = new User(uniqueEmail(), "test-only-hash");
        em.persist(user);
        return user;
    }

    private Document newDocument() {
        Document doc = new Document("test-document-" + UUID.randomUUID(), null, java.time.Instant.now());
        em.persist(doc);
        return doc;
    }

    private DocumentChunk newChunk(Document document, int index) {
        DocumentChunk chunk = new DocumentChunk(document, index, "sample official content");
        em.persist(chunk);
        return chunk;
    }

    private ResearchTask newTask(User user) {
        ResearchQuery query = new ResearchQuery(user, "청년 월세 지원 조건은?");
        em.persist(query);
        ResearchTask task = new ResearchTask(query);
        em.persist(task);
        return task;
    }

    private Answer newAnswer(ResearchTask task) {
        Answer answer = new Answer(task);
        em.persist(answer);
        return answer;
    }

    private Claim newClaim(Answer answer) {
        Claim claim = new Claim(answer, 0, "지원 자격은 일정 연령 범위이다.");
        em.persist(claim);
        return claim;
    }

    private void expectDbConstraintFailure(String expectedSqlState, Runnable statement) {
        RuntimeException failure = assertThrows(RuntimeException.class, () -> {
            statement.run();
            em.flush();
        }, "Expected PostgreSQL SQLSTATE " + expectedSqlState);

        String observedSqlState = null;
        Throwable cause = failure;
        while (cause != null) {
            if (cause instanceof java.sql.SQLException sqlException) {
                observedSqlState = sqlException.getSQLState();
                if (expectedSqlState.equals(observedSqlState)) {
                    return;
                }
                java.sql.SQLException next = sqlException.getNextException();
                while (next != null) {
                    observedSqlState = next.getSQLState();
                    if (expectedSqlState.equals(observedSqlState)) {
                        return;
                    }
                    next = next.getNextException();
                }
            }
            cause = cause.getCause();
        }
        fail("Expected PostgreSQL SQLSTATE " + expectedSqlState
                + " but observed " + observedSqlState
                + " from " + failure.getClass().getName(), failure);
    }

    // A. Basic entity persistence

    @Test
    void P01_userPersistAndReload() {
        User user = newUser();
        Long id = user.getUserId();
        em.flush();
        em.clear();
        User loaded = em.find(User.class, id);
        assertNotNull(loaded);
        assertNotNull(loaded.getCreatedAt());
        assertNotNull(loaded.getUpdatedAt());
    }

    @Test
    void P02_duplicateEmailRejected() {
        String email = uniqueEmail();
        em.persist(new User(email, "first-hash"));
        em.flush();
        expectDbConstraintFailure("23505", () -> em.persist(new User(email, "second-hash")));
    }

    @Test
    void P03_userConsentHistoryPreserved() {
        User user = newUser();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        em.persist(new UserConsent(user, ConsentType.TERMS, "v1", true, now.minusDays(1)));
        em.persist(new UserConsent(user, ConsentType.TERMS, "v1", false, now));
        em.flush();
        em.clear();
        Long total = em.createQuery(
                "select count(c) from UserConsent c where c.user.userId = :id", Long.class)
                .setParameter("id", user.getUserId())
                .getSingleResult();
        assertEquals(2L, total);
    }

    @Test
    void P04_documentInvalidEffectivePeriodRejected() {
        Document document = newDocument();
        document.updateValidity(LocalDate.now(), LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 11, 1));
        em.flush();
        expectDbConstraintFailure("23514", () -> em.createNativeQuery(
                "UPDATE document SET effective_to = DATE '2026-09-01' " +
                        "WHERE document_id = :id")
                .setParameter("id", document.getDocumentId())
                .executeUpdate());
    }

    @Test
    void P05_duplicateDocumentChunkIndexRejected() {
        Document doc = newDocument();
        newChunk(doc, 0);
        em.flush();
        expectDbConstraintFailure("23505", () -> newChunk(doc, 0));
    }

    // B. Query, research task, answer, claim

    @Test
    void P06_researchQueryUserAssociationReloaded() {
        User user = newUser();
        ResearchQuery query = new ResearchQuery(user, "학자금 대출 조건은?");
        em.persist(query);
        em.flush();
        Long id = query.getQueryId();
        Long userId = user.getUserId();
        em.clear();
        ResearchQuery loaded = em.find(ResearchQuery.class, id);
        assertEquals(userId, loaded.getUser().getUserId());
    }

    @Test
    void P07_researchTaskPreviewReloaded() {
        ResearchTask task = newTask(newUser());
        task.start();
        task.updateProgress((short) 3, "공식 공고 확인 중");
        em.flush();
        Long id = task.getTaskId();
        em.clear();
        ResearchTask loaded = em.find(ResearchTask.class, id);
        assertEquals(ResearchTaskStatus.RUNNING, loaded.getStatus());
        assertEquals(Short.valueOf((short) 3), loaded.getCurrentStep());
        assertEquals("공식 공고 확인 중", loaded.getPreviewSummary());
    }

    @Test
    void P08_twoAnswersForOneTaskRejected() {
        ResearchTask task = newTask(newUser());
        newAnswer(task);
        em.flush();
        expectDbConstraintFailure("23505", () -> newAnswer(task));
    }

    @Test
    void P09_finalAnswerWithoutContentRejected() {
        Answer answer = newAnswer(newTask(newUser()));
        assertThrows(IllegalArgumentException.class,
                () -> answer.markFinal(" ", "summary"));
        em.flush();
        expectDbConstraintFailure("23514", () -> em.createNativeQuery(
                "UPDATE answer SET answer_status = 'FINAL' WHERE answer_id = :id")
                .setParameter("id", answer.getAnswerId())
                .executeUpdate());
    }

    @Test
    void P10_duplicateClaimIndexRejected() {
        Answer answer = newAnswer(newTask(newUser()));
        newClaim(answer);
        em.flush();
        expectDbConstraintFailure("23505", () -> em.persist(new Claim(answer, 0, "중복 Claim")));
    }

    // C. Verification and retrieval constraints

    @Test
    void P11_duplicateVerificationAttemptRejected() {
        Claim claim = newClaim(newAnswer(newTask(newUser())));
        em.persist(new Verification(claim, 1));
        em.flush();
        expectDbConstraintFailure("23505", () -> em.persist(new Verification(claim, 1)));
    }

    @Test
    void P12_completedVerificationCannotBeChanged() {
        Claim claim = newClaim(newAnswer(newTask(newUser())));
        Verification verification = new Verification(claim, 1);
        em.persist(verification);
        verification.complete(VerificationStatus.PARTIAL, "일부만 일치");
        assertTrue(verification.isCompleted());
        assertThrows(IllegalStateException.class,
                () -> verification.complete(VerificationStatus.SUPPORTED, "재판정"));
        em.flush();
    }

    @Test
    void P13_invalidEvidenceRelationRejected() {
        DocumentChunk chunk = newChunk(newDocument(), 0);
        Claim claim = newClaim(newAnswer(newTask(newUser())));
        Verification verification = new Verification(claim, 1);
        em.persist(verification);
        Evidence evidence = new Evidence(verification, chunk, "공고 발췌",
                EvidenceRelation.SUPPORTS);
        em.persist(evidence);
        em.flush();
        expectDbConstraintFailure("23514", () -> em.createNativeQuery(
                "UPDATE evidence SET evidence_relation = 'UNKNOWN' WHERE evidence_id = :id")
                .setParameter("id", evidence.getEvidenceId())
                .executeUpdate());
    }

    @Test
    void P13B_nullEvidenceRelationRejectedByDatabase() {
        DocumentChunk chunk = newChunk(newDocument(), 0);
        Claim claim = newClaim(newAnswer(newTask(newUser())));
        Verification verification = new Verification(claim, 1);
        em.persist(verification);
        Evidence evidence = new Evidence(verification, chunk, "evidence excerpt",
                EvidenceRelation.SUPPORTS);
        em.persist(evidence);
        em.flush();
        expectDbConstraintFailure("23502", () -> em.createNativeQuery(
                "UPDATE evidence SET evidence_relation = NULL WHERE evidence_id = :id")
                .setParameter("id", evidence.getEvidenceId())
                .executeUpdate());
    }

    @Test
    void P14_retryLimitAndDuplicateAttemptRejected() {
        ResearchTask task = newTask(newUser());
        Claim claim = newClaim(newAnswer(task));
        assertThrows(IllegalArgumentException.class,
                () -> RetrievalRun.retry(task, claim, 4, "재검색 4회"));
        em.persist(RetrievalRun.retry(task, claim, 1, "재검색 1회"));
        em.flush();
        expectDbConstraintFailure("23505", () -> em.persist(
                RetrievalRun.retry(task, claim, 1, "재검색 1회 중복")));
    }

    @Test
    void P14B_retryAttemptFourRejectedByDatabase() {
        ResearchTask task = newTask(newUser());
        Claim claim = newClaim(newAnswer(task));
        RetrievalRun retry = RetrievalRun.retry(task, claim, 1, "first retry query");
        em.persist(retry);
        em.flush();
        expectDbConstraintFailure("23514", () -> em.createNativeQuery(
                "UPDATE retrieval_run SET re_attempt_number = 4 WHERE run_id = :id")
                .setParameter("id", retry.getRunId())
                .executeUpdate());
    }

    @Test
    void P15_duplicateResultRankRejected() {
        Document doc = newDocument();
        DocumentChunk chunk0 = newChunk(doc, 0);
        DocumentChunk chunk1 = newChunk(doc, 1);
        RetrievalRun run = RetrievalRun.initial(newTask(newUser()), "청약 정보 검색");
        em.persist(run);
        em.persist(new RetrievalResult(run, chunk0, 1, 0.93));
        em.flush();
        expectDbConstraintFailure("23505", () -> em.persist(
                new RetrievalResult(run, chunk1, 1, 0.88)));
    }

    @Test
    void P15B_duplicateResultChunkRejectedByDatabase() {
        Document doc = newDocument();
        DocumentChunk chunk = newChunk(doc, 0);
        RetrievalRun run = RetrievalRun.initial(newTask(newUser()), "source search");
        em.persist(run);
        em.persist(new RetrievalResult(run, chunk, 1, 0.93));
        em.flush();
        expectDbConstraintFailure("23505", () -> em.persist(
                new RetrievalResult(run, chunk, 2, 0.88)));
    }

    // D. JSONB and source-folder / saved-source rules

    @Test
    void P16_jsonbArraysRoundTrip() {
        EvaluationCase sample = new EvaluationCase(
                "청년 월세 지원은?", "지원 조건을 확인하세요",
                List.of(new ExpectedClaim(null, "연령 조건", null), new ExpectedClaim(null, "소득 조건", null)), List.of(10L, 20L), "주거");
        em.persist(sample);
        em.flush();
        Long id = sample.getEvalCaseId();
        em.clear();
        EvaluationCase loaded = em.find(EvaluationCase.class, id);
        assertEquals(List.of(new ExpectedClaim(null, "연령 조건", null), new ExpectedClaim(null, "소득 조건", null)), loaded.getExpectedClaims());
        assertEquals(List.of(10L, 20L), loaded.getRelevantChunkIds());
    }

    @Test
    void P17_jsonbNonArrayRejected() {
        EvaluationCase sample = new EvaluationCase(
                "평가 질문", null, List.of("Claim"), List.of(3L), null);
        em.persist(sample);
        em.flush();
        expectDbConstraintFailure("23514", () -> em.createNativeQuery(
                "UPDATE evaluation_case SET expected_claims = '{}'::jsonb " +
                        "WHERE eval_case_id = :id")
                .setParameter("id", sample.getEvalCaseId())
                .executeUpdate());
    }

    @Test
    void P18_sameUserDuplicateFolderNameRejected() {
        User user = newUser();
        em.persist(new SourceFolder(user, "청약"));
        em.flush();
        expectDbConstraintFailure("23505", () -> em.persist(new SourceFolder(user, "청약")));
    }

    @Test
    void P19_sameUserDocumentCannotBeSavedTwice() {
        User user = newUser();
        Document doc = newDocument();
        em.persist(SavedSource.manualSave(user, doc));
        em.flush();
        expectDbConstraintFailure("23505", () -> em.persist(SavedSource.manualSave(user, doc)));
    }

    @Test
    void P20_crossUserFolderOwnershipRejected() {
        User owner = newUser();
        User stranger = newUser();
        SourceFolder folder = new SourceFolder(owner, "개인 폴더");
        em.persist(folder);
        em.flush();
        SavedSource saved = SavedSource.manualSave(stranger, newDocument());
        saved.moveToFolder(folder.getFolderId());
        expectDbConstraintFailure("23503", () -> em.persist(saved));
    }

    @Test
    void P21_deleteFolderRetainsSavedSourceAndUser() {
        User user = newUser();
        Document doc = newDocument();
        SourceFolder folder = new SourceFolder(user, "삭제 예정 폴더");
        em.persist(folder);
        em.flush();
        SavedSource saved = SavedSource.manualSave(user, doc);
        saved.moveToFolder(folder.getFolderId());
        em.persist(saved);
        em.flush();
        Long savedId = saved.getSavedSourceId();
        Long ownerId = user.getUserId();
        Long docId = doc.getDocumentId();
        em.remove(folder);
        em.flush();
        em.clear();
        SavedSource reloaded = em.find(SavedSource.class, savedId);
        assertNotNull(reloaded, "The saved document must survive folder deletion");
        assertNull(reloaded.getFolderId(), "Only folder_id should become NULL");
        assertEquals(ownerId, reloaded.getUser().getUserId());
        assertEquals(docId, reloaded.getDocument().getDocumentId());
    }

    // E. Database transaction atomicity - NOT a Service integration test.

    private static final class IntentionalRollback extends RuntimeException { }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void P22_retrievalAndSavedSourceRollbackTogether() {
        String email = uniqueEmail();
        String title = "rollback-document-" + UUID.randomUUID();
        TransactionTemplate template = new TransactionTemplate(transactionManager);

        assertThrows(IntentionalRollback.class, () -> template.execute(status -> {
            User user = new User(email, "test-only-hash");
            em.persist(user);
            Document doc = new Document(title, null, java.time.Instant.now());
            em.persist(doc);
            DocumentChunk chunk = new DocumentChunk(doc, 0, "rollback probe chunk");
            em.persist(chunk);
            ResearchQuery query = new ResearchQuery(user, "rollback probe question");
            em.persist(query);
            ResearchTask task = new ResearchTask(query);
            em.persist(task);
            RetrievalRun run = RetrievalRun.initial(task, "rollback probe");
            em.persist(run);
            em.persist(new RetrievalResult(run, chunk, 1, 0.9));
            em.persist(SavedSource.autoSave(user, doc, task));
            em.flush();
            throw new IntentionalRollback();
        }));

        Long leftoverUserRows = jdbc.queryForObject(
                "SELECT count(*) FROM app_user WHERE email = ?", Long.class, email);
        Long leftoverResultRows = jdbc.queryForObject(
                "SELECT count(*) FROM retrieval_result r " +
                        "JOIN document_chunk c ON c.chunk_id = r.chunk_id " +
                        "JOIN document d ON d.document_id = c.document_id " +
                        "WHERE d.doc_title = ?", Long.class, title);
        Long leftoverSavedRows = jdbc.queryForObject(
                "SELECT count(*) FROM saved_source s " +
                        "JOIN document d ON d.document_id = s.document_id " +
                        "WHERE d.doc_title = ?", Long.class, title);
        assertEquals(0L, leftoverUserRows);
        assertEquals(0L, leftoverResultRows);
        assertEquals(0L, leftoverSavedRows);
    }

    @Autowired private com.example.cast.document.service.DocumentService documents;
    @Autowired private com.example.cast.document.repository.DocumentChunkRepository chunks;
    @Autowired private com.example.cast.claim.service.ClaimService claims;
    @Autowired private com.example.cast.claim.service.VerificationService verifications;
    @Autowired private com.example.cast.retrieval.service.RetrievalRunService runs;

    @Test
    void repeatedCollectionReusesOriginalDocument() {
        var request = new com.example.cast.document.dto.request.CollectDocumentRequest("original", new byte[] {1, 2, 3});
        Document first = documents.collect(request);
        Document second = documents.collect(new com.example.cast.document.dto.request.CollectDocumentRequest("another title", request.content()));
        assertEquals(first.getDocumentId(), second.getDocumentId());
        assertEquals("original", second.getDocTitle());
        assertEquals(64, first.getContentHash().length());
        assertNotNull(first.getCollectedAt());
    }

    @Test
    void invalidDocumentPeriodRejectedAtServiceBoundary() {
        Document document = newDocument();
        assertThrows(jakarta.validation.ConstraintViolationException.class, () -> documents.updateValidity(
                document.getDocumentId(), new com.example.cast.document.dto.request.DocumentValidityRequest(
                        null, LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 1))));
    }

    @Test
    void validityIncludesBoundaryAndExcludesExpiredDocuments() {
        Document valid = newDocument();
        Document expired = newDocument();
        LocalDate date = LocalDate.of(2026, 11, 7);
        valid.updateValidity(null, date, date);
        expired.updateValidity(null, null, date.minusDays(1));
        var validChunk = newChunk(valid, 0);
        var expiredChunk = newChunk(expired, 0);
        em.flush();
        var ids = chunks.findValidOn(date).stream().map(DocumentChunk::getChunkId).toList();
        assertTrue(ids.contains(validChunk.getChunkId()));
        assertFalse(ids.contains(expiredChunk.getChunkId()));
    }

    @Test
    void claimCanChangeOnlyBeforeFirstVerification() {
        Claim claim = newClaim(newAnswer(newTask(newUser())));
        var request = new com.example.cast.claim.dto.request.ClaimRequest("정규화된 금액", ClaimType.AMOUNT,
                java.util.Map.of("amount", 300000, "unit", "KRW"));
        claims.revise(claim.getClaimId(), request);
        em.flush(); em.clear();
        Claim stored = em.find(Claim.class, claim.getClaimId());
        assertEquals(ClaimType.AMOUNT, stored.getClaimType());
        assertEquals(300000, stored.getClaimValue().get("amount"));
        verifications.start(claim.getClaimId(), null);
        var failure = assertThrows(com.example.cast.global.exception.BusinessException.class,
                () -> claims.revise(claim.getClaimId(), request));
        assertEquals(com.example.cast.claim.code.ClaimErrorCode.CLAIM_ALREADY_VERIFIED, failure.getErrorCode());
    }

    @Test
    void retrievalRejectsClaimFromAnotherTask() {
        Claim claim = newClaim(newAnswer(newTask(newUser())));
        ResearchTask other = newTask(newUser());
        var request = new com.example.cast.retrieval.dto.request.RetrievalRunRequest(other.getTaskId(),
                claim.getClaimId(), RetrievalRunType.CLAIM_SEARCH, 0, "search");
        var failure = assertThrows(com.example.cast.global.exception.BusinessException.class, () -> runs.create(request));
        assertEquals(com.example.cast.retrieval.code.RetrievalErrorCode.CLAIM_TASK_MISMATCH, failure.getErrorCode());
    }

    @Test
    void verificationRecordsMethodResultsAndRun() {
        ResearchTask task = newTask(newUser());
        Claim claim = newClaim(newAnswer(task));
        RetrievalRun run = runs.create(new com.example.cast.retrieval.dto.request.RetrievalRunRequest(
                task.getTaskId(), claim.getClaimId(), RetrievalRunType.CLAIM_SEARCH, 0, "search"));
        Verification verification = verifications.start(claim.getClaimId(), run.getRunId());
        verifications.complete(verification.getVerificationId(), new com.example.cast.claim.dto.request.VerificationResultRequest(
                VerificationStatus.PARTIAL, "판정 차이", VerificationMethod.HYBRID,
                VerificationStatus.CONTRADICTED, VerificationStatus.SUPPORTED, 0.7));
        em.flush(); em.clear();
        var stored = em.find(Verification.class, verification.getVerificationId());
        assertEquals(VerificationMethod.HYBRID, stored.getVerificationMethod());
        assertEquals(VerificationStatus.CONTRADICTED, stored.getRuleResult());
        assertEquals(VerificationStatus.SUPPORTED, stored.getLlmResult());
        assertEquals(run.getRunId(), stored.getRetrievalRun().getRunId());
        assertEquals(0.7, stored.getConfidence());
    }

    @Test
    void invalidConfidenceRejectedByDatabase() {
        var verification = new Verification(newClaim(newAnswer(newTask(newUser()))), 1);
        em.persist(verification); em.flush();
        expectDbConstraintFailure("23514", () -> em.createNativeQuery(
                "update verification set confidence = 'NaN'::float8 where verification_id = :id")
                .setParameter("id", verification.getVerificationId()).executeUpdate());
    }

    @Test
    void auditingUpdatesModificationTime() {
        User user = newUser(); em.flush();
        var created = user.getCreatedAt();
        user.updatePasswordHash("replacement"); em.flush();
        assertEquals(created, user.getCreatedAt());
        assertFalse(user.getUpdatedAt().isBefore(created));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentCollectionUsesOneRow() throws Exception {
        byte[] content = UUID.randomUUID().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var ready = new java.util.concurrent.CountDownLatch(2);
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Long> collect = () -> {
                ready.countDown();
                if (!start.await(10, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("start timeout");
                return documents.collect(new com.example.cast.document.dto.request.CollectDocumentRequest("concurrent", content)).getDocumentId();
            };
            var first = executor.submit(collect);
            var second = executor.submit(collect);
            assertTrue(ready.await(10, java.util.concurrent.TimeUnit.SECONDS));
            start.countDown();
            assertEquals(first.get(20, java.util.concurrent.TimeUnit.SECONDS), second.get(20, java.util.concurrent.TimeUnit.SECONDS));
        } finally { start.countDown(); }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentVerificationStartsAllocateDistinctAttempts() throws Exception {
        var tx = new TransactionTemplate(transactionManager);
        Long claimId = tx.execute(status -> newClaim(newAnswer(newTask(newUser()))).getClaimId());
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Integer> begin = () -> {
                if (!start.await(10, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("start timeout");
                return verifications.start(claimId, null).getVerAttemptNumber();
            };
            var first = executor.submit(begin);
            var second = executor.submit(begin);
            start.countDown();
            var attempts = java.util.Set.of(first.get(20, java.util.concurrent.TimeUnit.SECONDS),
                    second.get(20, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(java.util.Set.of(1, 2), attempts);
        } finally { start.countDown(); }
    }
}
