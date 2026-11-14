package com.example.cast.source;

import com.example.cast.auth.dto.request.SignupRequest;
import com.example.cast.auth.dto.response.AuthResponse;
import com.example.cast.auth.service.AuthService;
import com.example.cast.persistence.TestDatabaseSupport;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class SourceDocumentDetailIntegrationTest extends TestDatabaseSupport {
    private static final AtomicLong AI_DOCUMENT_ID = new AtomicLong(8_000_000);

    @Autowired MockMvc mvc;
    @Autowired AuthService auth;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;

    @Test
    void autoSavedDocumentReturnsMetadataAndOriginTask() throws Exception {
        var account = signup();
        long folderId = folderId(account.userId(), "HOUSING");
        long taskId = newResearchTask(account.userId());
        long documentId = newDocument("가상 청년 지원 정책", true);
        long savedSourceId = save(account.userId(), documentId, folderId, "AUTO", true, taskId);

        JsonNode result = detail(account, savedSourceId);
        assertThat(result.path("savedSourceId").asLong()).isEqualTo(savedSourceId);
        assertThat(result.path("documentId").asLong()).isEqualTo(documentId);
        assertThat(result.path("documentTitle").asString()).isEqualTo("가상 청년 지원 정책");
        assertThat(result.path("sourceName").asString()).isEqualTo("가상 청년지원센터");
        assertThat(result.path("sourceUrl").asString()).isEqualTo("https://example.go.kr/document");
        assertThat(result.path("publishedAt").asString()).isEqualTo("2026-10-01");
        assertThat(result.path("effectiveFrom").asString()).isEqualTo("2026-11-01");
        assertThat(result.path("effectiveTo").asString()).isEqualTo("2027-10-31");
        assertThat(result.path("documentVersion").asString()).isEqualTo("2026-1");
        assertThat(result.path("sourceType").asString()).isEqualTo("GOVERNMENT");
        assertThat(result.path("reliabilityLevel").asString()).isEqualTo("HIGH");
        assertThat(result.path("saveMethod").asString()).isEqualTo("AUTO");
        assertThat(result.path("folderId").asLong()).isEqualTo(folderId);
        assertThat(result.path("favorite").asBoolean()).isTrue();
        assertThat(result.path("originTaskId").asLong()).isEqualTo(taskId);
        assertThat(result.path("savedAt").asString()).isNotBlank();
    }

    @Test
    void manualSavedDocumentKeepsNullableFieldsNull() throws Exception {
        var account = signup();
        long documentId = newDocument("수동 저장 문서", false);
        long savedSourceId = save(account.userId(), documentId, null, "MANUAL", false, null);

        JsonNode result = detail(account, savedSourceId);
        assertThat(result.path("documentTitle").asString()).isEqualTo("수동 저장 문서");
        assertThat(result.path("sourceType").asString()).isEqualTo("REPORT");
        assertThat(result.path("saveMethod").asString()).isEqualTo("MANUAL");
        assertThat(result.path("sourceName").isNull()).isTrue();
        assertThat(result.path("sourceUrl").isNull()).isTrue();
        assertThat(result.path("publishedAt").isNull()).isTrue();
        assertThat(result.path("effectiveFrom").isNull()).isTrue();
        assertThat(result.path("effectiveTo").isNull()).isTrue();
        assertThat(result.path("documentVersion").isNull()).isTrue();
        assertThat(result.path("reliabilityLevel").isNull()).isTrue();
        assertThat(result.path("folderId").isNull()).isTrue();
        assertThat(result.path("originTaskId").isNull()).isTrue();
        assertThat(result.path("favorite").asBoolean()).isFalse();
    }

    @Test
    void otherUsersSavedDocumentIsNotFound() throws Exception {
        var owner = signup();
        var other = signup();
        long documentId = newDocument("타인의 문서", false);
        long savedSourceId = save(owner.userId(), documentId, null, "MANUAL", false, null);

        mvc.perform(get("/api/v1/sources/documents/{savedSourceId}", savedSourceId)
                        .header("Authorization", bearer(other)))
                .andExpect(status().isNotFound());
        detail(owner, savedSourceId);
    }

    @Test
    void nonExistingSavedDocumentReturns404() throws Exception {
        var account = signup();
        mvc.perform(get("/api/v1/sources/documents/{savedSourceId}", Long.MAX_VALUE)
                        .header("Authorization", bearer(account)))
                .andExpect(status().isNotFound());
    }

    @Test
    void invalidSavedSourceIdsReturn400() throws Exception {
        var account = signup();
        for (String id : new String[] {"0", "-1", "abc"}) {
            mvc.perform(get("/api/v1/sources/documents/{savedSourceId}", id)
                            .header("Authorization", bearer(account)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void missingJwtReturns401() throws Exception {
        mvc.perform(get("/api/v1/sources/documents/{savedSourceId}", 1))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void detailIncludesEvidenceRelatedResearchAndScraps() throws Exception {
        var account = signup();
        long documentId = newDocument("검증 연결 문서", true);
        var linked = completeResearchWithEvidence(account.userId(), documentId, "지원 대상은 청년이다.");
        long savedSourceId = save(account.userId(), documentId, null, "AUTO", false, linked.taskId());
        long scrapId = newScrap(account.userId(), linked.evidenceId());

        JsonNode result = detail(account, savedSourceId);
        assertThat(result.path("verificationEvidenceTotalCount").asLong()).isEqualTo(1);
        assertThat(result.path("verificationEvidenceHasMore").asBoolean()).isFalse();
        JsonNode evidence = result.path("verificationEvidences").get(0);
        assertThat(evidence.path("evidenceId").asLong()).isEqualTo(linked.evidenceId());
        assertThat(evidence.path("claimContent").asString()).isEqualTo("지원 대상은 청년이다.");
        assertThat(evidence.path("verificationStatus").asString()).isEqualTo("SUPPORTED");
        assertThat(evidence.path("taskId").asLong()).isEqualTo(linked.taskId());
        assertThat(evidence.path("pageStart").asInt()).isEqualTo(3);
        assertThat(evidence.path("sectionTitle").asString()).isEqualTo("지원 대상");
        assertThat(evidence.path("isScrapped").asBoolean()).isTrue();
        assertThat(evidence.path("scrapId").asLong()).isEqualTo(scrapId);

        assertThat(result.path("relatedResearchTotalCount").asLong()).isEqualTo(1);
        JsonNode research = result.path("relatedResearches").get(0);
        assertThat(research.path("taskId").asLong()).isEqualTo(linked.taskId());
        assertThat(research.path("question").asString()).isEqualTo("테스트 연구 질문");
        assertThat(research.path("summaryContent").asString()).isEqualTo("최종 답변 요약");
        assertThat(research.path("completedAt").asString()).isNotBlank();

        assertThat(result.path("scrappedSentenceTotalCount").asLong()).isEqualTo(1);
        JsonNode scrap = result.path("scrappedSentences").get(0);
        assertThat(scrap.path("scrapId").asLong()).isEqualTo(scrapId);
        assertThat(scrap.path("evidenceId").asLong()).isEqualTo(linked.evidenceId());
        assertThat(scrap.path("scrappedAt").asString()).isNotBlank();
    }

    @Test
    void detailShowsOnlyLatestCompletedVerificationButKeepsEarlierScraps() throws Exception {
        var account = signup();
        long documentId = newDocument("재검증 문서", false);
        var linked = completeResearchWithEvidence(account.userId(), documentId, "지원 금액 주장");
        long savedSourceId = save(account.userId(), documentId, null, "AUTO", false, linked.taskId());
        long oldScrap = newScrap(account.userId(), linked.evidenceId());

        long recentVerification = jdbc.queryForObject("""
                INSERT INTO backend.verification(claim_id, status, ver_attempt_number)
                VALUES (?, 'CONTRADICTED', 2) RETURNING verification_id
                """, Long.class, linked.claimId());
        long newEvidence = jdbc.queryForObject("""
                INSERT INTO backend.evidence(verification_id, document_id, evidence_text, evidence_relation)
                VALUES (?, ?, '새 검증 문장', 'CONTRADICTS') RETURNING evidence_id
                """, Long.class, recentVerification, documentId);
        jdbc.update("""
                INSERT INTO backend.verification(claim_id, status, ver_attempt_number)
                VALUES (?, 'IN_PROGRESS', 3)
                """, linked.claimId());

        JsonNode result = detail(account, savedSourceId);
        assertThat(result.path("verificationEvidenceTotalCount").asLong()).isEqualTo(1);
        assertThat(result.path("verificationEvidences").get(0).path("evidenceId").asLong())
                .isEqualTo(newEvidence);
        assertThat(result.path("verificationEvidences").get(0).path("verificationStatus").asString())
                .isEqualTo("CONTRADICTED");
        assertThat(result.path("verificationEvidences").get(0).path("isScrapped").asBoolean())
                .isFalse();
        assertThat(result.path("scrappedSentenceTotalCount").asLong()).isEqualTo(1);
        assertThat(result.path("scrappedSentences").get(0).path("scrapId").asLong())
                .isEqualTo(oldScrap);
        assertThat(result.path("scrappedSentences").get(0).path("evidenceId").asLong())
                .isEqualTo(linked.evidenceId());
    }

    @Test
    void detailNeverLeaksOtherUsersResearchOrScraps() throws Exception {
        var owner = signup();
        var other = signup();
        long documentId = newDocument("공유 원문", false);
        long savedSourceId = save(owner.userId(), documentId, null, "MANUAL", false, null);
        var otherLinked = completeResearchWithEvidence(other.userId(), documentId, "타인 주장");
        newScrap(other.userId(), otherLinked.evidenceId());

        JsonNode ownerResult = detail(owner, savedSourceId);
        assertThat(ownerResult.path("verificationEvidences").size()).isZero();
        assertThat(ownerResult.path("verificationEvidenceTotalCount").asLong()).isZero();
        assertThat(ownerResult.path("relatedResearches").size()).isZero();
        assertThat(ownerResult.path("relatedResearchTotalCount").asLong()).isZero();
        assertThat(ownerResult.path("scrappedSentences").size()).isZero();
        assertThat(ownerResult.path("scrappedSentenceTotalCount").asLong()).isZero();
    }

    @Test
    void detailCountsAllItemsButReturnsAtMostTwentyPerSection() throws Exception {
        var account = signup();
        long documentId = newDocument("대량 검증 문서", false);
        var linked = completeResearchWithEvidence(account.userId(), documentId, "근거 다수 주장");
        long savedSourceId = save(account.userId(), documentId, null, "AUTO", false, linked.taskId());
        newScrap(account.userId(), linked.evidenceId());
        long verificationId = jdbc.queryForObject("""
                SELECT verification_id FROM backend.evidence WHERE evidence_id = ?
                """, Long.class, linked.evidenceId());
        for (int i = 1; i < 21; i++) {
            long evidenceId = jdbc.queryForObject("""
                    INSERT INTO backend.evidence(verification_id, document_id, evidence_text,
                                                evidence_relation)
                    VALUES (?, ?, ?, 'SUPPORTS') RETURNING evidence_id
                    """, Long.class, verificationId, documentId, "추가 근거 " + i);
            newScrap(account.userId(), evidenceId);
        }
        JsonNode result = detail(account, savedSourceId);
        assertThat(result.path("verificationEvidenceTotalCount").asLong()).isEqualTo(21);
        assertThat(result.path("verificationEvidences").size()).isEqualTo(20);
        assertThat(result.path("verificationEvidenceHasMore").asBoolean()).isTrue();
        assertThat(result.path("scrappedSentenceTotalCount").asLong()).isEqualTo(21);
        assertThat(result.path("scrappedSentences").size()).isEqualTo(20);
        assertThat(result.path("scrappedSentenceHasMore").asBoolean()).isTrue();
        assertThat(result.path("relatedResearchTotalCount").asLong()).isEqualTo(1);
    }

    @Test
    void detailIncludesOriginTaskEvenWhenItIsNotFinished() throws Exception {
        var account = signup();
        long documentId = newDocument("진행 중 원본", false);
        long taskId = newResearchTask(account.userId());
        long savedSourceId = save(account.userId(), documentId, null, "AUTO", false, taskId);

        JsonNode result = detail(account, savedSourceId);
        assertThat(result.path("relatedResearchTotalCount").asLong()).isEqualTo(1);
        assertThat(result.path("relatedResearches").get(0).path("taskId").asLong())
                .isEqualTo(taskId);
        assertThat(result.path("relatedResearches").get(0).path("completedAt").isNull()).isTrue();
        assertThat(result.path("verificationEvidenceTotalCount").asLong()).isZero();
    }

    private LinkedEvidence completeResearchWithEvidence(long userId, long documentId,
            String claimText) {
        long taskId = newResearchTask(userId);
        jdbc.update("""
                UPDATE backend.research_query SET research_title = '연관 리서치'
                WHERE query_id = (SELECT query_id FROM backend.research_task WHERE task_id = ?)
                """, taskId);
        jdbc.update("""
                UPDATE backend.research_task SET status = 'COMPLETED', completed_at = NOW()
                WHERE task_id = ?
                """, taskId);
        long answerId = jdbc.queryForObject("""
                INSERT INTO backend.answer(task_id, answer_content, summary_content, answer_status)
                VALUES (?, '최종 답변', '최종 답변 요약', 'FINAL') RETURNING answer_id
                """, Long.class, taskId);
        long claimId = jdbc.queryForObject("""
                INSERT INTO backend.claim(answer_id, claim_index, claim_content, claim_status)
                VALUES (?, 0, ?, 'CONFIRMED') RETURNING claim_id
                """, Long.class, answerId, claimText);
        long verificationId = jdbc.queryForObject("""
                INSERT INTO backend.verification(claim_id, status, ver_attempt_number)
                VALUES (?, 'SUPPORTED', 1) RETURNING verification_id
                """, Long.class, claimId);
        long evidenceId = jdbc.queryForObject("""
                INSERT INTO backend.evidence(verification_id, document_id, evidence_text,
                                             evidence_relation, page_start, page_end, section_title)
                VALUES (?, ?, '검증 근거 원문', 'SUPPORTS', 3, 3, '지원 대상')
                RETURNING evidence_id
                """, Long.class, verificationId, documentId);
        return new LinkedEvidence(taskId, claimId, evidenceId);
    }

    private long newScrap(long userId, long evidenceId) {
        return jdbc.queryForObject("""
                INSERT INTO backend.sentence_scrap(user_id, evidence_id)
                VALUES (?, ?) RETURNING scrap_id
                """, Long.class, userId, evidenceId);
    }

    private record LinkedEvidence(long taskId, long claimId, long evidenceId) { }

    private AuthResponse signup() {
        return auth.signup(new SignupRequest(
                UUID.randomUUID() + "@example.invalid", "SecurePassword123!"));
    }

    private String bearer(AuthResponse user) {
        return "Bearer " + user.accessToken();
    }

    private JsonNode detail(AuthResponse account, long savedSourceId) throws Exception {
        var response = mvc.perform(get("/api/v1/sources/documents/{savedSourceId}", savedSourceId)
                        .header("Authorization", bearer(account))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        var body = mapper.readTree(response.getResponse().getContentAsByteArray());
        assertThat(body.path("code").asString()).isEqualTo("COMMON200");
        return body.path("result");
    }

    private long folderId(long userId, String category) {
        return jdbc.queryForObject("""
                SELECT folder_id FROM backend.source_folder
                WHERE user_id = ? AND folder_category = ?
                """, Long.class, userId, category);
    }

    private long newResearchTask(long userId) {
        long queryId = jdbc.queryForObject("""
                INSERT INTO backend.research_query (user_id, question)
                VALUES (?, '테스트 연구 질문') RETURNING query_id
                """, Long.class, userId);
        return jdbc.queryForObject("""
                INSERT INTO backend.research_task (query_id)
                VALUES (?) RETURNING task_id
                """, Long.class, queryId);
    }

    private long newDocument(String title, boolean fullMetadata) {
        long aiDocumentId = AI_DOCUMENT_ID.incrementAndGet();
        if (!fullMetadata) {
            return jdbc.queryForObject("""
                    INSERT INTO backend.document (ai_document_id, doc_title, source_type)
                    VALUES (?, ?, 'REPORT') RETURNING document_id
                    """, Long.class, aiDocumentId, title);
        }
        return jdbc.queryForObject("""
                INSERT INTO backend.document (
                    ai_document_id, doc_title, source_name, source_url,
                    published_at, effective_from, effective_to, doc_version,
                    source_type, reliability_level)
                VALUES (?, ?, '가상 청년지원센터', 'https://example.go.kr/document',
                    DATE '2026-10-01', DATE '2026-11-01', DATE '2027-10-31',
                    '2026-1', 'GOVERNMENT', 'HIGH') RETURNING document_id
                """, Long.class, aiDocumentId, title);
    }

    private long save(long userId, long documentId, Long folderId,
                      String method, boolean favorite, Long originTaskId) {
        return jdbc.queryForObject("""
                INSERT INTO backend.saved_source
                    (user_id, document_id, folder_id, save_method, is_favorite, origin_task_id)
                VALUES (?, ?, ?, ?, ?, ?) RETURNING saved_source_id
                """, Long.class, userId, documentId, folderId, method, favorite, originTaskId);
    }
}
