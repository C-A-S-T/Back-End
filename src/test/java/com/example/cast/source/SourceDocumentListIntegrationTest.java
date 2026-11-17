package com.example.cast.source;

import com.example.cast.auth.dto.request.SignupRequest;
import com.example.cast.auth.dto.response.AuthResponse;
import com.example.cast.auth.service.AuthService;
import com.example.cast.persistence.TestDatabaseSupport;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class SourceDocumentListIntegrationTest extends TestDatabaseSupport {
    private static final AtomicLong AI_DOCUMENT_ID = new AtomicLong(5_000_000);

    @Autowired MockMvc mvc;
    @Autowired AuthService auth;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;

    @Test
    void emptyListReturnsPageMetadata() throws Exception {
        var account = signup();
        var result = fetch(account, null, 0, 20);
        assertThat(result.path("items").isArray()).isTrue();
        assertThat(result.path("items").size()).isZero();
        assertThat(result.path("page").asInt()).isZero();
        assertThat(result.path("size").asInt()).isEqualTo(20);
        assertThat(result.path("totalElements").asLong()).isZero();
        assertThat(result.path("totalPages").asInt()).isZero();
        assertThat(result.path("hasNext").asBoolean()).isFalse();
    }

    @Test
    void listsAndFiltersSavedDocumentsWithStableOrderAndUserIsolation() throws Exception {
        var owner = signup();
        var other = signup();
        long housing = folderId(owner.userId(), "HOUSING");
        long gov = folderId(owner.userId(), "GOV_SUPPORT");
        long otherHousing = folderId(other.userId(), "HOUSING");

        long first = newDocument("housing AUTO");
        long second = newDocument("housing MANUAL");
        long third = newDocument("government AUTO");
        long fourth = newDocument("unclassified MANUAL");
        long foreign = newDocument("other user's document");

        save(owner.userId(), first, housing, "AUTO", "2026-11-01T00:00:00Z");
        save(owner.userId(), second, housing, "MANUAL", "2026-11-02T00:00:00Z");
        save(owner.userId(), third, gov, "AUTO", "2026-11-03T00:00:00Z");
        save(owner.userId(), fourth, null, "MANUAL", "2026-11-04T00:00:00Z");
        save(other.userId(), foreign, otherHousing, "AUTO", "2026-11-05T00:00:00Z");

        var page0 = fetch(owner, null, 0, 2);
        assertThat(page0.path("totalElements").asLong()).isEqualTo(4);
        assertThat(page0.path("totalPages").asInt()).isEqualTo(2);
        assertThat(page0.path("hasNext").asBoolean()).isTrue();
        assertThat(page0.path("items").get(0).path("documentId").asLong()).isEqualTo(fourth);
        assertThat(page0.path("items").get(0).path("folderId").isNull()).isTrue();
        assertThat(page0.path("items").get(1).path("documentId").asLong()).isEqualTo(third);

        var housingPage = fetch(owner, housing, 0, 20);
        assertThat(housingPage.path("totalElements").asLong()).isEqualTo(2);
        assertThat(housingPage.path("items").get(0).path("documentId").asLong()).isEqualTo(second);
        assertThat(housingPage.path("items").get(0).path("saveMethod").asString()).isEqualTo("MANUAL");
        assertThat(housingPage.path("items").get(0).has("favorite")).isFalse();
        assertThat(housingPage.path("items").get(1).path("documentId").asLong()).isEqualTo(first);
        assertThat(housingPage.path("items").get(1).path("saveMethod").asString()).isEqualTo("AUTO");

        var secondPage = fetch(owner, null, 1, 2);
        assertThat(secondPage.path("items").size()).isEqualTo(2);
        assertThat(secondPage.path("hasNext").asBoolean()).isFalse();

        var otherPage = fetch(other, null, 0, 20);
        assertThat(otherPage.path("totalElements").asLong()).isEqualTo(1);
        assertThat(otherPage.path("items").get(0).path("documentId").asLong()).isEqualTo(foreign);
    }

    @Test
    void invalidParametersAndOtherUsersFolderAreRejected() throws Exception {
        var owner = signup();
        var other = signup();
        long foreignFolder = folderId(other.userId(), "HOUSING");

        mvc.perform(get("/api/v1/sources/documents").header("Authorization", bearer(owner))
                        .param("page", "-1"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/sources/documents").header("Authorization", bearer(owner))
                        .param("size", "101"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/sources/documents").header("Authorization", bearer(owner))
                        .param("folderId", "0"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/sources/documents").header("Authorization", bearer(owner))
                        .param("folderId", String.valueOf(foreignFolder)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/sources/documents").header("Authorization", bearer(owner))
                        .param("folderId", "999999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void unauthorizedRequestReturns401() throws Exception {
        mvc.perform(get("/api/v1/sources/documents"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void keywordMatchesTitleOrSourceNameWithoutCaseSensitivityOrUserLeak() throws Exception {
        var owner = signup();
        var other = signup();
        long titleMatch = newDocument("Youth Housing Guide", "기관A", "REPORT");
        long sourceMatch = newDocument("일반 안내", "청년지원센터", "GOVERNMENT");
        long foreign = newDocument("Youth Private", "청년지원센터", "GOVERNMENT");
        save(owner.userId(), titleMatch, null, "MANUAL", "2026-11-01T00:00:00Z");
        save(owner.userId(), sourceMatch, null, "AUTO", "2026-11-02T00:00:00Z");
        save(other.userId(), foreign, null, "AUTO", "2026-11-03T00:00:00Z");

        var english = fetchWithFilter(owner, "keyword", " yOuTh ");
        assertThat(english.path("totalElements").asLong()).isEqualTo(1);
        assertThat(english.path("items").get(0).path("documentId").asLong()).isEqualTo(titleMatch);
        var korean = fetchWithFilter(owner, "keyword", "청년");
        assertThat(korean.path("totalElements").asLong()).isEqualTo(1);
        assertThat(korean.path("items").get(0).path("documentId").asLong()).isEqualTo(sourceMatch);
    }

    @Test
    void wildcardCharactersAreLiteralAndBlankKeywordMeansNoSearch() throws Exception {
        var owner = signup();
        long underscore = newDocument("정책_2026");
        long ordinary = newDocument("정책X2026");
        long percent = newDocument("100% 지원");
        save(owner.userId(), underscore, null, "MANUAL", "2026-11-01T00:00:00Z");
        save(owner.userId(), ordinary, null, "MANUAL", "2026-11-02T00:00:00Z");
        save(owner.userId(), percent, null, "MANUAL", "2026-11-03T00:00:00Z");

        var underscoreResult = fetchWithFilter(owner, "keyword", "_");
        assertThat(underscoreResult.path("totalElements").asLong()).isEqualTo(1);
        assertThat(underscoreResult.path("items").get(0).path("documentId").asLong()).isEqualTo(underscore);
        var percentResult = fetchWithFilter(owner, "keyword", "%");
        assertThat(percentResult.path("totalElements").asLong()).isEqualTo(1);
        assertThat(percentResult.path("items").get(0).path("documentId").asLong()).isEqualTo(percent);
        assertThat(fetchWithFilter(owner, "keyword", "   ")
                .path("totalElements").asLong()).isEqualTo(3);
    }

    @Test
    void combinedFiltersPreserveFolderSecurityAndExcludeNonMatchingDocuments() throws Exception {
        var owner = signup();
        var other = signup();
        long housing = folderId(owner.userId(), "HOUSING");
        long government = folderId(owner.userId(), "GOV_SUPPORT");
        long foreignFolder = folderId(other.userId(), "HOUSING");
        long selected = newDocument("청년 정책 A", "센터", "GOVERNMENT");
        long differentMethod = newDocument("청년 정책 B", "센터", "GOVERNMENT");
        long notType = newDocument("청년 연구", "센터", "REPORT");
        long notFolder = newDocument("청년 정책 C", "센터", "GOVERNMENT");
        save(owner.userId(), selected, housing, "AUTO", "2026-11-01T00:00:00Z");
        save(owner.userId(), differentMethod, housing, "MANUAL", "2026-11-02T00:00:00Z");
        save(owner.userId(), notType, housing, "MANUAL", "2026-11-03T00:00:00Z");
        save(owner.userId(), notFolder, government, "AUTO", "2026-11-04T00:00:00Z");

        var request = get("/api/v1/sources/documents")
                .header("Authorization", bearer(owner))
                .param("folderId", String.valueOf(housing))
                .param("keyword", "청년")
                .param("sourceType", "GOVERNMENT")
                .param("saveMethod", "AUTO");
        var response = mvc.perform(request).andExpect(status().isOk()).andReturn();
        var result = mapper.readTree(response.getResponse().getContentAsByteArray()).path("result");
        assertThat(result.path("totalElements").asLong()).isEqualTo(1);
        assertThat(result.path("items").get(0).path("documentId").asLong()).isEqualTo(selected);

        assertThat(fetch(owner, null, 0, 20).path("totalElements").asLong()).isEqualTo(4);

        mvc.perform(get("/api/v1/sources/documents")
                        .header("Authorization", bearer(owner))
                        .param("folderId", String.valueOf(foreignFolder)).param("keyword", "청년"))
                .andExpect(status().isNotFound());
    }

    @Test
    void filteredPaginationAndEmptyResultsHaveCorrectMetadata() throws Exception {
        var owner = signup();
        long first = newDocument("청년 안내 1");
        long second = newDocument("청년 안내 2");
        long third = newDocument("청년 안내 3");
        long unrelated = newDocument("일반 안내");
        save(owner.userId(), first, null, "MANUAL", "2026-11-01T00:00:00Z");
        save(owner.userId(), second, null, "MANUAL", "2026-11-02T00:00:00Z");
        save(owner.userId(), third, null, "MANUAL", "2026-11-03T00:00:00Z");
        save(owner.userId(), unrelated, null, "MANUAL", "2026-11-04T00:00:00Z");

        var firstPage = fetchFilteredPage(owner, "청년", 0, 2);
        assertThat(firstPage.path("totalElements").asLong()).isEqualTo(3);
        assertThat(firstPage.path("totalPages").asInt()).isEqualTo(2);
        assertThat(firstPage.path("hasNext").asBoolean()).isTrue();
        assertThat(firstPage.path("items").get(0).path("documentId").asLong()).isEqualTo(third);
        assertThat(firstPage.path("items").get(1).path("documentId").asLong()).isEqualTo(second);
        var nextPage = fetchFilteredPage(owner, "청년", 1, 2);
        assertThat(nextPage.path("totalElements").asLong()).isEqualTo(3);
        assertThat(nextPage.path("hasNext").asBoolean()).isFalse();
        assertThat(nextPage.path("items").get(0).path("documentId").asLong()).isEqualTo(first);
        var empty = fetchWithFilter(owner, "keyword", "없는검색어");
        assertThat(empty.path("items").size()).isZero();
        assertThat(empty.path("totalElements").asLong()).isZero();
    }

    @Test
    void invalidSearchParametersReturn400() throws Exception {
        var owner = signup();
        for (String[] param : new String[][] {
                {"keyword", "가".repeat(101)},
                {"sourceType", "UNKNOWN"},
                {"saveMethod", "INVALID"}
        }) {
            mvc.perform(get("/api/v1/sources/documents")
                            .header("Authorization", bearer(owner))
                            .param(param[0], param[1]))
                    .andExpect(status().isBadRequest());
        }
    }

    private JsonNode fetchWithFilter(AuthResponse account, String key, String value) throws Exception {
        return fetchFilteredPage(account, key.equals("keyword") ? value : null, 0, 20, key, value);
    }

    private JsonNode fetchFilteredPage(AuthResponse account, String keyword, int page, int size)
            throws Exception {
        return fetchFilteredPage(account, keyword, page, size, null, null);
    }

    private JsonNode fetchFilteredPage(AuthResponse account, String keyword, int page, int size,
            String extraKey, String extraValue) throws Exception {
        var request = get("/api/v1/sources/documents")
                .header("Authorization", bearer(account))
                .param("page", String.valueOf(page)).param("size", String.valueOf(size));
        if (keyword != null) request.param("keyword", keyword);
        if (extraKey != null && !extraKey.equals("keyword")) request.param(extraKey, extraValue);
        var response = mvc.perform(request).andExpect(status().isOk()).andReturn();
        return mapper.readTree(response.getResponse().getContentAsByteArray()).path("result");
    }

    private AuthResponse signup() {
        return auth.signup(new SignupRequest(
                UUID.randomUUID() + "@example.invalid", "SecurePassword123!"));
    }

    private String bearer(AuthResponse account) {
        return "Bearer " + account.accessToken();
    }

    private JsonNode fetch(AuthResponse account, Long folderId, int page, int size) throws Exception {
        var request = get("/api/v1/sources/documents")
                .header("Authorization", bearer(account))
                .param("page", String.valueOf(page))
                .param("size", String.valueOf(size))
                .accept(MediaType.APPLICATION_JSON);
        if (folderId != null) request.param("folderId", String.valueOf(folderId));
        var response = mvc.perform(request).andExpect(status().isOk()).andReturn();
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

    private long newDocument(String title) {
        return newDocument(title, "공공기관", "GOVERNMENT");
    }

    private long newDocument(String title, String sourceName, String sourceType) {
        return jdbc.queryForObject("""
                INSERT INTO backend.document
                    (ai_document_id, doc_title, source_name, source_url, published_at,
                     source_type, reliability_level)
                VALUES (?, ?, ?, 'https://example.go.kr/document',
                        DATE '2026-10-15', ?, 'HIGH')
                RETURNING document_id
                """, Long.class, AI_DOCUMENT_ID.incrementAndGet(), title, sourceName, sourceType);
    }

    private void save(long userId, long documentId, Long folderId,
                      String method, String savedAt) {
        var timestamp = Timestamp.from(Instant.parse(savedAt));
        jdbc.update("""
                INSERT INTO backend.saved_source
                    (user_id, document_id, folder_id, save_method,
                     created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """, userId, documentId, folderId, method, timestamp, timestamp);
    }
}
