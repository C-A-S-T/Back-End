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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class PolicyChangeIntegrationTest extends TestDatabaseSupport {
    private static final AtomicLong AI_DOCUMENT_ID = new AtomicLong(9_500_000L);

    @Autowired MockMvc mvc;
    @Autowired AuthService auth;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;

    @Test
    void noConfirmedMatchesReturnEmptyPageAndOverviewCountIsStillUnknown() throws Exception {
        var user = signup();
        var result = fetch(user, "missing-" + UUID.randomUUID(), null, 0, 20);
        assertThat(result.path("items").isArray()).isTrue();
        assertThat(result.path("items").size()).isZero();
        assertThat(result.path("totalElements").asLong()).isZero();
        assertThat(result.path("totalPages").asInt()).isZero();
        assertThat(result.path("hasNext").asBoolean()).isFalse();

        var overview = mvc.perform(get("/api/v1/sources/overview")
                .header("Authorization", bearer(user))).andExpect(status().isOk()).andReturn();
        assertThat(mapper.readTree(overview.getResponse().getContentAsByteArray())
                .path("result").path("changedPolicyCount").isNull()).isTrue();
    }

    @Test
    void allUsersCanReadConfirmedChangesWithoutSavingAnyDocument() throws Exception {
        var a = signup();
        var b = signup();
        long previous = newDocument("2025");
        long current = newDocument("2026");
        String title = "공통정책-" + UUID.randomUUID();
        long change = addChange(title, previous, current, "2026-11-10T09:00:00Z");

        for (var account : new AuthResponse[]{a, b}) {
            var result = fetch(account, title, null, 0, 20);
            assertThat(result.path("totalElements").asLong()).isEqualTo(1);
            var item = result.path("items").get(0);
            assertThat(item.path("policyChangeId").asLong()).isEqualTo(change);
            assertThat(item.path("policyTitle").asString()).isEqualTo(title);
            assertThat(item.path("previousDocumentId").asLong()).isEqualTo(previous);
            assertThat(item.path("previousDocumentVersion").asString()).isEqualTo("2025");
            assertThat(item.path("currentDocumentVersion").asString()).isEqualTo("2026");
            assertThat(item.path("category").asString()).isEqualTo("GOV_SUPPORT");
            assertThat(item.path("requiresRecheck").isNull()).isTrue();
            assertThat(item.path("impactSummary").isNull()).isTrue();
        }
    }

    @Test
    void impactIsScopedToCurrentUserAndNullDoesNotMeanFalse() throws Exception {
        var needsRecheck = signup();
        var doesNotNeed = signup();
        var notEvaluated = signup();
        String title = "사용자영향-" + UUID.randomUUID();
        long change = addChange(title, newDocument("before"), newDocument("after"),
                "2026-11-11T09:00:00Z");
        addImpact(needsRecheck.userId(), change, true, "조건을 다시 확인하세요.");
        addImpact(doesNotNeed.userId(), change, false, "현재 주장에 영향이 없습니다.");

        var yes = fetch(needsRecheck, title, true, 0, 20);
        assertThat(yes.path("totalElements").asLong()).isEqualTo(1);
        assertThat(yes.path("items").get(0).path("requiresRecheck").asBoolean()).isTrue();
        assertThat(yes.path("items").get(0).path("impactSummary").asString())
                .isEqualTo("조건을 다시 확인하세요.");
        assertThat(fetch(needsRecheck, title, false, 0, 20)
                .path("totalElements").asLong()).isZero();

        var no = fetch(doesNotNeed, title, false, 0, 20);
        assertThat(no.path("totalElements").asLong()).isEqualTo(1);
        assertThat(no.path("items").get(0).path("requiresRecheck").asBoolean()).isFalse();
        assertThat(no.path("items").get(0).path("impactSummary").asString())
                .isEqualTo("현재 주장에 영향이 없습니다.");
        assertThat(fetch(doesNotNeed, title, true, 0, 20)
                .path("totalElements").asLong()).isZero();

        var unknown = fetch(notEvaluated, title, null, 0, 20);
        assertThat(unknown.path("totalElements").asLong()).isEqualTo(1);
        assertThat(unknown.path("items").get(0).path("requiresRecheck").isNull()).isTrue();
        assertThat(unknown.path("items").get(0).path("impactSummary").isNull()).isTrue();
        assertThat(fetch(notEvaluated, title, true, 0, 20)
                .path("totalElements").asLong()).isZero();
        assertThat(fetch(notEvaluated, title, false, 0, 20)
                .path("totalElements").asLong()).isZero();
    }

    @Test
    void keywordSearchIgnoresCaseAndTreatsWildcardsLiterally() throws Exception {
        var user = signup();
        String unique = UUID.randomUUID().toString().substring(0, 8);
        String title = "Youth_100%!" + unique;
        addChange(title, newDocument("v1"), newDocument("v2"), "2026-11-10T09:00:00Z");

        assertThat(fetch(user, title.toLowerCase(java.util.Locale.ROOT), null, 0, 20)
                .path("totalElements").asLong()).isEqualTo(1);
        assertThat(fetch(user, "_100%!" + unique, null, 0, 20)
                .path("totalElements").asLong()).isEqualTo(1);
        assertThat(fetch(user, "%!" + unique, null, 0, 20)
                .path("totalElements").asLong()).isEqualTo(1);
        assertThat(fetch(user, " ", null, 0, 20).path("items").isArray()).isTrue();
    }

    @Test
    void paginationUsesConfirmedDateThenIdAndPreservesFilteredTotal() throws Exception {
        var user = signup();
        String prefix = "페이지-" + UUID.randomUUID() + "-";
        long old = addChange(prefix + "older", newDocument("old-a"), newDocument("new-a"),
                "2026-11-01T00:00:00Z");
        long recent = addChange(prefix + "newer", newDocument("old-b"), newDocument("new-b"),
                "2026-11-10T00:00:00Z");

        var first = fetch(user, prefix, null, 0, 1);
        assertThat(first.path("totalElements").asLong()).isEqualTo(2);
        assertThat(first.path("totalPages").asInt()).isEqualTo(2);
        assertThat(first.path("hasNext").asBoolean()).isTrue();
        assertThat(first.path("items").get(0).path("policyChangeId").asLong()).isEqualTo(recent);

        var second = fetch(user, prefix, null, 1, 1);
        assertThat(second.path("items").get(0).path("policyChangeId").asLong()).isEqualTo(old);
        assertThat(second.path("hasNext").asBoolean()).isFalse();
    }

    @Test
    void migrationConstraintsPreventInvalidOrDuplicateComparisons() {
        long old = newDocument("old");
        long next = newDocument("next");
        String title = "제약조건-" + UUID.randomUUID();
        String key = "policy-" + UUID.randomUUID();
        addChangeWithKey(key, title, old, next, "2026-11-10T00:00:00Z");
        assertThatThrownBy(() -> addChangeWithKey(key, title, old, next,
                "2026-11-10T00:00:00Z"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> addChangeWithKey("key-" + UUID.randomUUID(), title,
                old, old, "2026-11-10T00:00:00Z"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> addChangeWithKey("key-" + UUID.randomUUID(), title,
                old, Long.MAX_VALUE, "2026-11-10T00:00:00Z"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void invalidParametersAndUnauthenticatedRequestsAreRejected() throws Exception {
        var user = signup();
        mvc.perform(get("/api/v1/sources/policy-changes"))
                .andExpect(status().isUnauthorized());
        for (var pair : new String[][] {
                {"page", "-1"}, {"size", "0"}, {"size", "101"},
                {"keyword", "x".repeat(101)}, {"requiresRecheck", "not-a-boolean"}
        }) {
            mvc.perform(get("/api/v1/sources/policy-changes")
                    .header("Authorization", bearer(user))
                    .param(pair[0], pair[1]))
                    .andExpect(status().isBadRequest());
        }
    }

    private AuthResponse signup() {
        return auth.signup(new SignupRequest(
                UUID.randomUUID() + "@example.invalid", "SecurePassword123!"));
    }

    private String bearer(AuthResponse account) {
        return "Bearer " + account.accessToken();
    }

    private JsonNode fetch(AuthResponse user, String keyword,
            Boolean requiresRecheck, int page, int size) throws Exception {
        var request = get("/api/v1/sources/policy-changes")
                .header("Authorization", bearer(user))
                .param("page", String.valueOf(page))
                .param("size", String.valueOf(size))
                .accept(MediaType.APPLICATION_JSON);
        if (keyword != null) request.param("keyword", keyword);
        if (requiresRecheck != null) request.param("requiresRecheck", String.valueOf(requiresRecheck));
        var response = mvc.perform(request).andExpect(status().isOk()).andReturn();
        var body = mapper.readTree(response.getResponse().getContentAsByteArray());
        assertThat(body.path("code").asString()).isEqualTo("COMMON200");
        return body.path("result");
    }

    private long newDocument(String version) {
        return jdbc.queryForObject("""
                INSERT INTO backend.document
                    (ai_document_id, doc_title, doc_version, source_type)
                VALUES (?, ?, ?, 'GOVERNMENT')
                RETURNING document_id
                """, Long.class, AI_DOCUMENT_ID.incrementAndGet(), "검증용 문서", version);
    }

    private long addChange(String title, long old, long current, String timestamp) {
        return addChangeWithKey("policy-" + UUID.randomUUID(), title, old, current, timestamp);
    }

    private long addChangeWithKey(String key, String title, long old, long current,
            String timestamp) {
        return jdbc.queryForObject("""
                INSERT INTO backend.policy_change
                    (policy_key, policy_title, category, previous_document_id,
                     current_document_id, change_summary, confirmed_at)
                VALUES (?, ?, 'GOV_SUPPORT', ?, ?, '검증용 변경 요약', ?)
                RETURNING policy_change_id
                """, Long.class, key, title, old, current,
                Timestamp.from(Instant.parse(timestamp)));
    }

    private void addImpact(long userId, long changeId, Boolean requiresRecheck, String summary) {
        jdbc.update("""
                INSERT INTO backend.user_policy_change_impact
                    (user_id, policy_change_id, requires_recheck, impact_summary, evaluated_at)
                VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
                """, userId, changeId, requiresRecheck, summary);
    }
}
