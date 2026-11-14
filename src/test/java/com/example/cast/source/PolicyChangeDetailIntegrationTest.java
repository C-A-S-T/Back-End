package com.example.cast.source;

import com.example.cast.auth.dto.request.SignupRequest;
import com.example.cast.auth.dto.response.AuthResponse;
import com.example.cast.auth.service.AuthService;
import com.example.cast.persistence.TestDatabaseSupport;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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
class PolicyChangeDetailIntegrationTest extends TestDatabaseSupport {
    private static final AtomicLong AI_DOCUMENT_ID = new AtomicLong(9_800_000L);

    @Autowired MockMvc mvc;
    @Autowired AuthService auth;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;

    @Test
    void detailIncludesFullPreviousAndCurrentDocumentMetadata() throws Exception {
        var user = signup();
        long previous = document("이전 공고", "2025", "https://example.org/2025", "HIGH");
        long current = document("변경 공고", "2026", "https://example.org/2026", null);
        long changeId = change(previous, current);

        var result = fetch(user, changeId);
        assertThat(result.path("policyChangeId").asLong()).isEqualTo(changeId);
        assertThat(result.path("policyTitle").asString()).isEqualTo("가상 정책 변경");
        assertThat(result.path("category").asString()).isEqualTo("GOV_SUPPORT");
        assertThat(result.path("changeSummary").asString()).isEqualTo("검증용 변경 요약");
        assertThat(result.path("confirmedAt").asString()).isEqualTo("2026-11-10T09:00:00Z");

        var old = result.path("previousDocument");
        assertThat(old.path("documentId").asLong()).isEqualTo(previous);
        assertThat(old.path("documentTitle").asString()).isEqualTo("이전 공고");
        assertThat(old.path("documentVersion").asString()).isEqualTo("2025");
        assertThat(old.path("sourceName").asString()).isEqualTo("가상 정책센터");
        assertThat(old.path("sourceUrl").asString()).isEqualTo("https://example.org/2025");
        assertThat(old.path("publishedAt").asString()).isEqualTo("2025-02-01");
        assertThat(old.path("effectiveFrom").asString()).isEqualTo("2025-03-01");
        assertThat(old.path("effectiveTo").asString()).isEqualTo("2025-12-31");
        assertThat(old.path("sourceType").asString()).isEqualTo("GOVERNMENT");
        assertThat(old.path("reliabilityLevel").asString()).isEqualTo("HIGH");

        var next = result.path("currentDocument");
        assertThat(next.path("documentId").asLong()).isEqualTo(current);
        assertThat(next.path("documentTitle").asString()).isEqualTo("변경 공고");
        assertThat(next.path("documentVersion").asString()).isEqualTo("2026");
        assertThat(next.path("sourceUrl").asString()).isEqualTo("https://example.org/2026");
        assertThat(next.path("publishedAt").asString()).isEqualTo("2026-02-01");
        assertThat(next.path("effectiveFrom").asString()).isEqualTo("2026-03-01");
        assertThat(next.path("effectiveTo").asString()).isEqualTo("2026-12-31");
        assertThat(next.path("reliabilityLevel").isNull()).isTrue();
    }

    @Test
    void allUsersCanReadDetailWithoutSavedSources() throws Exception {
        var a = signup();
        var b = signup();
        long changeId = change(document("이전", "2025", null, null),
                document("이후", "2026", null, null));
        for (var account : new AuthResponse[]{a, b}) {
            assertThat(fetch(account, changeId).path("policyChangeId").asLong())
                    .isEqualTo(changeId);
        }
    }

    @Test
    void impactIsOnlyReturnedForCurrentUser() throws Exception {
        var owner = signup();
        var outsider = signup();
        long changeId = change(document("이전", "2025", null, null),
                document("이후", "2026", null, null));
        impact(owner.userId(), changeId, true, "재검증 필요");

        var ownerResult = fetch(owner, changeId);
        assertThat(ownerResult.path("requiresRecheck").asBoolean()).isTrue();
        assertThat(ownerResult.path("impactSummary").asString()).isEqualTo("재검증 필요");
        assertThat(ownerResult.path("evaluatedAt").isNull()).isFalse();

        var outsiderResult = fetch(outsider, changeId);
        assertThat(outsiderResult.path("requiresRecheck").isNull()).isTrue();
        assertThat(outsiderResult.path("impactSummary").isNull()).isTrue();
        assertThat(outsiderResult.path("evaluatedAt").isNull()).isTrue();
    }

    @Test
    void unevaluatedImpactHasNullFields() throws Exception {
        var user = signup();
        long changeId = change(document("이전", "2025", null, null),
                document("이후", "2026", null, null));
        impact(user.userId(), changeId, null, null);
        var result = fetch(user, changeId);
        assertThat(result.path("requiresRecheck").isNull()).isTrue();
        assertThat(result.path("impactSummary").isNull()).isTrue();
        assertThat(result.path("evaluatedAt").isNull()).isTrue();
    }

    @Test
    void evaluatedFalseIsDifferentFromUnknown() throws Exception {
        var user = signup();
        long changeId = change(document("이전", "2025", null, null),
                document("이후", "2026", null, null));
        impact(user.userId(), changeId, false, "재검증 불필요");
        var result = fetch(user, changeId);
        assertThat(result.path("requiresRecheck").isBoolean()).isTrue();
        assertThat(result.path("requiresRecheck").asBoolean()).isFalse();
        assertThat(result.path("impactSummary").asString()).isEqualTo("재검증 불필요");
        assertThat(result.path("evaluatedAt").isNull()).isFalse();
    }

    @Test
    void missingPolicyChangeReturnsNotFound() throws Exception {
        var user = signup();
        mvc.perform(get("/api/v1/sources/policy-changes/{id}", Long.MAX_VALUE)
                        .header("Authorization", bearer(user)))
                .andExpect(status().isNotFound());
    }

    @Test
    void invalidIdsReturnBadRequest() throws Exception {
        var user = signup();
        for (String id : new String[]{"0", "-1", "not-a-number"}) {
            mvc.perform(get("/api/v1/sources/policy-changes/{id}", id)
                            .header("Authorization", bearer(user)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void missingJwtIsUnauthorized() throws Exception {
        mvc.perform(get("/api/v1/sources/policy-changes/{id}", 1L))
                .andExpect(status().isUnauthorized());
    }

    private AuthResponse signup() {
        return auth.signup(new SignupRequest(
                UUID.randomUUID() + "@example.invalid", "SecurePassword123!"));
    }

    private String bearer(AuthResponse account) {
        return "Bearer " + account.accessToken();
    }

    private JsonNode fetch(AuthResponse user, long changeId) throws Exception {
        var response = mvc.perform(get("/api/v1/sources/policy-changes/{id}", changeId)
                        .header("Authorization", bearer(user)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = mapper.readTree(response.getResponse().getContentAsByteArray());
        assertThat(body.path("code").asString()).isEqualTo("COMMON200");
        return body.path("result");
    }

    private long document(String title, String version, String url, String reliability) {
        String year = version.equals("2025") ? "2025" : "2026";
        return jdbc.queryForObject("""
                INSERT INTO backend.document (
                    ai_document_id, doc_title, source_name, source_url,
                    doc_version, published_at, effective_from, effective_to,
                    source_type, reliability_level)
                VALUES (?, ?, '가상 정책센터', ?, ?, ?, ?, ?, 'GOVERNMENT', ?)
                RETURNING document_id
                """, Long.class, AI_DOCUMENT_ID.incrementAndGet(), title, url, version,
                Date.valueOf(LocalDate.parse(year + "-02-01")),
                Date.valueOf(LocalDate.parse(year + "-03-01")),
                Date.valueOf(LocalDate.parse(year + "-12-31")), reliability);
    }

    private long change(long previous, long current) {
        return jdbc.queryForObject("""
                INSERT INTO backend.policy_change (
                    policy_key, policy_title, category, previous_document_id,
                    current_document_id, change_summary, confirmed_at)
                VALUES (?, '가상 정책 변경', 'GOV_SUPPORT', ?, ?, '검증용 변경 요약', ?)
                RETURNING policy_change_id
                """, Long.class, "policy-" + UUID.randomUUID(), previous, current,
                Timestamp.from(Instant.parse("2026-11-10T09:00:00Z")));
    }

    private void impact(long userId, long changeId, Boolean requiresRecheck, String summary) {
        jdbc.update("""
                INSERT INTO backend.user_policy_change_impact (
                    user_id, policy_change_id, requires_recheck, impact_summary, evaluated_at)
                VALUES (?, ?, ?, ?, ?)
                """, userId, changeId, requiresRecheck, summary,
                requiresRecheck == null ? null : Timestamp.from(Instant.parse("2026-11-11T01:00:00Z")));
    }
}
