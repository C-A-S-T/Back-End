package com.example.cast.source;

import com.example.cast.auth.dto.request.SignupRequest;
import com.example.cast.auth.dto.response.AuthResponse;
import com.example.cast.auth.service.AuthService;
import com.example.cast.persistence.TestDatabaseSupport;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class SentenceScrapIntegrationTest extends TestDatabaseSupport {
    private static final AtomicLong AI_DOCUMENT_IDS = new AtomicLong(10_000_000);

    @Autowired MockMvc mvc;
    @Autowired AuthService auth;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;

    @Test
    void evidenceReadExposesIdsAndScrapState() throws Exception {
        var user = signup();
        var f = fixture(user, "청년 지원금 안내", "지원금은 월 20만 원이다.",
                "지원금은 월 30만 원이다.", "CONTRADICTED", "GOVERNMENT");
        JsonNode before = evidences(user, f.taskId());
        assertThat(before.path("claims").size()).isEqualTo(1);
        JsonNode item = before.path("claims").get(0).path("evidences").get(0);
        assertThat(item.path("evidenceId").asLong()).isEqualTo(f.evidenceId());
        assertThat(item.path("isScrapped").asBoolean()).isFalse();
        assertThat(item.path("scrapId").isNull()).isTrue();
        assertThat(before.path("claims").get(0).path("verificationStatus").asString())
                .isEqualTo("CONTRADICTED");

        long scrapId = save(user, f.evidenceId(), 201).path("scrapId").asLong();
        JsonNode after = evidences(user, f.taskId());
        item = after.path("claims").get(0).path("evidences").get(0);
        assertThat(item.path("isScrapped").asBoolean()).isTrue();
        assertThat(item.path("scrapId").asLong()).isEqualTo(scrapId);
    }

    @Test
    void saveIsIdempotentAndCountsOnce() throws Exception {
        var user = signup();
        var f = fixture(user, "주거 정책 공고", "지원 대상은 청년이다.",
                "청년이 지원받는다.", "SUPPORTED", "GOVERNMENT");
        JsonNode first = save(user, f.evidenceId(), 201);
        JsonNode second = save(user, f.evidenceId(), 200);
        assertThat(first.path("created").asBoolean()).isTrue();
        assertThat(second.path("created").asBoolean()).isFalse();
        assertThat(second.path("scrapId").asLong()).isEqualTo(first.path("scrapId").asLong());
        assertThat(second.path("scrappedAt").asString()).isEqualTo(first.path("scrappedAt").asString());
        assertThat(scraps(user, "").path("totalElements").asLong()).isEqualTo(1);
        assertThat(overview(user).path("scrappedSentenceCount").asLong()).isEqualTo(1);
    }

    @Test
    void onlyOwnersCanReadOrSaveEvidenceAndDeleteScraps() throws Exception {
        var owner = signup();
        var other = signup();
        var f = fixture(owner, "사용자 격리", "타인 근거", "타인 주장", "SUPPORTED", "REPORT");
        long scrapId = save(owner, f.evidenceId(), 201).path("scrapId").asLong();
        mvc.perform(get("/api/research-tasks/{taskId}/evidences", f.taskId())
                .header("Authorization", bearer(other))).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/sources/scraps")
                .header("Authorization", bearer(other)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"evidenceId\":" + f.evidenceId() + "}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/sources/scraps/{id}", scrapId)
                .header("Authorization", bearer(other))).andExpect(status().isNotFound());
        assertThat(scraps(other, "").path("totalElements").asLong()).isZero();
        assertThat(scraps(owner, "").path("totalElements").asLong()).isEqualTo(1);
    }

    @Test
    void deleteOnlyScrapAndKeepEvidence() throws Exception {
        var user = signup();
        var f = fixture(user, "스크랩 삭제", "삭제하지 않을 근거", "관련 주장", "SUPPORTED", "PAPER");
        long id = save(user, f.evidenceId(), 201).path("scrapId").asLong();
        mvc.perform(delete("/api/v1/sources/scraps/{id}", id)
                .header("Authorization", bearer(user))).andExpect(status().isOk());
        assertThat(scraps(user, "").path("totalElements").asLong()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM backend.evidence WHERE evidence_id = ?",
                Long.class, f.evidenceId())).isEqualTo(1L);
        assertThat(evidences(user, f.taskId()).path("claims").get(0)
                .path("evidences").get(0).path("isScrapped").asBoolean()).isFalse();
        mvc.perform(delete("/api/v1/sources/scraps/{id}", id)
                .header("Authorization", bearer(user))).andExpect(status().isNotFound());
    }

    @Test
    void listSearchesTextClaimAndTitleAndFiltersByStatusAndType() throws Exception {
        var user = signup();
        var housing = fixture(user, "청년월세 지원사업", "월 20만 원을 지원한다.",
                "월 30만 원을 지급한다.", "CONTRADICTED", "GOVERNMENT");
        var report = fixture(user, "연간 교육 보고서", "교육비를 지원한다.",
                "교육 혜택이 있다.", "SUPPORTED", "REPORT");
        save(user, housing.evidenceId(), 201);
        save(user, report.evidenceId(), 201);
        assertThat(scraps(user, "keyword=20만").path("totalElements").asLong()).isEqualTo(1);
        assertThat(scraps(user, "keyword=30만").path("totalElements").asLong()).isEqualTo(1);
        assertThat(scraps(user, "keyword=교육+보고서").path("totalElements").asLong()).isEqualTo(1);
        JsonNode result = scraps(user, "verificationStatus=CONTRADICTED&sourceType=GOVERNMENT");
        assertThat(result.path("totalElements").asLong()).isEqualTo(1);
        assertThat(result.path("items").get(0).path("evidenceRelation").asString())
                .isEqualTo("CONTRADICTS");
        assertThat(scraps(user, "verificationStatus=SUPPORTED").path("totalElements").asLong())
                .isEqualTo(1);
    }

    @Test
    void folderFilterUsesSavedSourceButUnfilteredScrapDoesNotNeedOne() throws Exception {
        var user = signup();
        var other = signup();
        var f = fixture(user, "폴더 검색", "폴더 근거", "폴더 주장", "SUPPORTED", "GOVERNMENT");
        save(user, f.evidenceId(), 201);
        long folderId = folderId(user.userId(), "HOUSING");
        long foreignFolder = folderId(other.userId(), "HOUSING");
        assertThat(scraps(user, "").path("totalElements").asLong()).isEqualTo(1);
        assertThat(scraps(user, "folderId=" + folderId).path("totalElements").asLong()).isZero();
        jdbc.update("""
                INSERT INTO backend.saved_source (user_id, document_id, folder_id, save_method)
                VALUES (?, ?, ?, 'AUTO')
                """, user.userId(), f.documentId(), folderId);
        JsonNode result = scraps(user, "folderId=" + folderId);
        assertThat(result.path("totalElements").asLong()).isEqualTo(1);
        assertThat(result.path("items").get(0).path("folderId").asLong()).isEqualTo(folderId);
        assertThat(result.path("items").get(0).path("savedSourceId").asLong()).isPositive();
        mvc.perform(get("/api/v1/sources/scraps").header("Authorization", bearer(user))
                .param("folderId", Long.toString(foreignFolder)))
                .andExpect(status().isNotFound());
    }

    @Test
    void paginationStableSortAndZeroCount() throws Exception {
        var user = signup();
        assertThat(overview(user).path("scrappedSentenceCount").asLong()).isZero();
        var first = fixture(user, "문서 1", "근거 1", "주장 1", "SUPPORTED", "NEWS");
        var second = fixture(user, "문서 2", "근거 2", "주장 2", "PARTIAL", "REPORT");
        long firstId = save(user, first.evidenceId(), 201).path("scrapId").asLong();
        long secondId = save(user, second.evidenceId(), 201).path("scrapId").asLong();
        var sameDate = Timestamp.from(Instant.parse("2026-11-12T00:00:00Z"));
        jdbc.update("UPDATE backend.sentence_scrap SET created_at = ? WHERE scrap_id IN (?, ?)",
                sameDate, firstId, secondId);
        JsonNode page0 = scraps(user, "page=0&size=1");
        JsonNode page1 = scraps(user, "page=1&size=1");
        assertThat(page0.path("totalElements").asLong()).isEqualTo(2);
        assertThat(page0.path("totalPages").asInt()).isEqualTo(2);
        assertThat(page0.path("hasNext").asBoolean()).isTrue();
        assertThat(page0.path("items").get(0).path("scrapId").asLong()).isEqualTo(secondId);
        assertThat(page1.path("items").get(0).path("scrapId").asLong()).isEqualTo(firstId);
        assertThat(page1.path("hasNext").asBoolean()).isFalse();
    }

    @Test
    void invalidParametersAndMissingResourcesAreHandled() throws Exception {
        var user = signup();
        for (String query : new String[] {"page=-1", "size=101", "folderId=0",
                "verificationStatus=NOT_A_STATUS", "sourceType=INVALID", "keyword=" + "x".repeat(220)}) {
            mvc.perform(scrapRequest(user, query)).andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/v1/sources/scraps").header("Authorization", bearer(user))
                .contentType(MediaType.APPLICATION_JSON).content("{\"evidenceId\":-1}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/sources/scraps").header("Authorization", bearer(user))
                .contentType(MediaType.APPLICATION_JSON).content("{\"evidenceId\":999999999}"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/research-tasks/{taskId}/evidences", 999999999)
                .header("Authorization", bearer(user))).andExpect(status().isNotFound());
    }

    @Test
    void latestCompletedVerificationIsShownButEarlierScrapIsRetained() throws Exception {
        var user = signup();
        var f = fixture(user, "정책 버전", "이전 근거", "지원 금액 주장", "CONTRADICTED", "REPORT");
        save(user, f.evidenceId(), 201);
        long oldVerification = jdbc.queryForObject("""
                SELECT verification_id FROM backend.evidence WHERE evidence_id = ?
                """, Long.class, f.evidenceId());
        long claimId = jdbc.queryForObject("""
                SELECT claim_id FROM backend.verification WHERE verification_id = ?
                """, Long.class, oldVerification);
        long newVerification = jdbc.queryForObject("""
                INSERT INTO backend.verification(claim_id,status,ver_attempt_number)
                VALUES (?,'SUPPORTED',2) RETURNING verification_id
                """, Long.class, claimId);
        long recentEvidence = jdbc.queryForObject("""
                INSERT INTO backend.evidence(verification_id,document_id,evidence_text,evidence_relation)
                VALUES (?,?,'최신 근거','SUPPORTS') RETURNING evidence_id
                """, Long.class, newVerification, f.documentId());
        JsonNode claims = evidences(user, f.taskId()).path("claims");
        assertThat(claims.get(0).path("verificationStatus").asString()).isEqualTo("SUPPORTED");
        JsonNode current = claims.get(0).path("evidences");
        assertThat(current.size()).isEqualTo(1);
        assertThat(current.get(0).path("evidenceId").asLong()).isEqualTo(recentEvidence);
        assertThat(scraps(user, "").path("items").get(0).path("evidenceId").asLong())
                .isEqualTo(f.evidenceId());
    }

    @Test
    void unfinishedResearchDoesNotExposeOrAllowScraps() throws Exception {
        var user = signup();
        var f = fixture(user, "진행 중 작업", "임시 근거", "임시 주장", "SUPPORTED", "NEWS");
        jdbc.update("UPDATE backend.research_task SET status = 'RUNNING' WHERE task_id = ?", f.taskId());
        assertThat(evidences(user, f.taskId()).path("claims").size()).isZero();
        mvc.perform(post("/api/v1/sources/scraps")
                .header("Authorization", bearer(user)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"evidenceId\":" + f.evidenceId() + "}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void allEndpointsRequireJwt() throws Exception {
        mvc.perform(get("/api/research-tasks/1/evidences")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/sources/scraps")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/sources/scraps").contentType(MediaType.APPLICATION_JSON)
                .content("{\"evidenceId\":1}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/v1/sources/scraps/1")).andExpect(status().isUnauthorized());
    }

    private AuthResponse signup() {
        return auth.signup(new SignupRequest(UUID.randomUUID() + "@example.invalid", "SecurePassword123!"));
    }

    private String bearer(AuthResponse user) {
        return "Bearer " + user.accessToken();
    }

    private JsonNode save(AuthResponse user, long evidenceId, int statusCode) throws Exception {
        var response = mvc.perform(post("/api/v1/sources/scraps")
                        .header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"evidenceId\":" + evidenceId + "}"))
                .andExpect(status().is(statusCode)).andReturn();
        return mapper.readTree(response.getResponse().getContentAsByteArray()).path("result");
    }

    private JsonNode scraps(AuthResponse user, String query) throws Exception {
        var response = mvc.perform(scrapRequest(user, query))
                .andExpect(status().isOk()).andReturn();
        return mapper.readTree(response.getResponse().getContentAsByteArray()).path("result");
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder scrapRequest(
            AuthResponse user, String query) {
        var request = get("/api/v1/sources/scraps")
                .header("Authorization", bearer(user));
        if (!query.isBlank()) {
            for (String pair : query.split("&")) {
                String[] kv = pair.split("=", 2);
                request.param(kv[0], URLDecoder.decode(kv.length > 1 ? kv[1] : "",
                        StandardCharsets.UTF_8));
            }
        }
        return request;
    }

    private JsonNode evidences(AuthResponse user, long taskId) throws Exception {
        var response = mvc.perform(get("/api/research-tasks/{taskId}/evidences", taskId)
                        .header("Authorization", bearer(user)))
                .andExpect(status().isOk()).andReturn();
        return mapper.readTree(response.getResponse().getContentAsByteArray()).path("result");
    }

    private JsonNode overview(AuthResponse user) throws Exception {
        var response = mvc.perform(get("/api/v1/sources/overview")
                .header("Authorization", bearer(user)))
                .andExpect(status().isOk()).andReturn();
        return mapper.readTree(response.getResponse().getContentAsByteArray()).path("result");
    }

    private long folderId(long userId, String category) {
        return jdbc.queryForObject("""
                SELECT folder_id FROM backend.source_folder
                WHERE user_id = ? AND folder_category = ?
                """, Long.class, userId, category);
    }

    private Fixture fixture(AuthResponse user, String title, String evidenceText,
                            String claimText, String status, String sourceType) {
        long aiId = AI_DOCUMENT_IDS.incrementAndGet();
        long doc = jdbc.queryForObject("""
                INSERT INTO backend.document(ai_document_id,doc_title,source_name,source_url,source_type)
                VALUES (?,?,'테스트 기관','https://example.go.kr/original',?) RETURNING document_id
                """, Long.class, aiId, title, sourceType);
        long queryId = jdbc.queryForObject("""
                INSERT INTO backend.research_query(user_id,question)
                VALUES (?, '테스트 리서치') RETURNING query_id
                """, Long.class, user.userId());
        long task = jdbc.queryForObject("""
                INSERT INTO backend.research_task(query_id,status)
                VALUES (?, 'COMPLETED') RETURNING task_id
                """, Long.class, queryId);
        long answer = jdbc.queryForObject("""
                INSERT INTO backend.answer(task_id,answer_content,answer_status)
                VALUES (?,'완료된 답변','FINAL') RETURNING answer_id
                """, Long.class, task);
        long claim = jdbc.queryForObject("""
                INSERT INTO backend.claim(answer_id,claim_index,claim_content,claim_status)
                VALUES (?,0,?,'CONFIRMED') RETURNING claim_id
                """, Long.class, answer, claimText);
        long verification = jdbc.queryForObject("""
                INSERT INTO backend.verification(claim_id,status,ver_attempt_number)
                VALUES (?,?,1) RETURNING verification_id
                """, Long.class, claim, status);
        String relation = "CONTRADICTED".equals(status) ? "CONTRADICTS" : "SUPPORTS";
        long evidence = jdbc.queryForObject("""
                INSERT INTO backend.evidence(verification_id,document_id,evidence_text,
                                             evidence_relation,page_start,page_end)
                VALUES (?,?,?,?,1,1) RETURNING evidence_id
                """, Long.class, verification, doc, evidenceText, relation);
        return new Fixture(task, doc, evidence);
    }

    private record Fixture(long taskId, long documentId, long evidenceId) {}
}
