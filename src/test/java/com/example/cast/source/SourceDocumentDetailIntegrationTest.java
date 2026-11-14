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
