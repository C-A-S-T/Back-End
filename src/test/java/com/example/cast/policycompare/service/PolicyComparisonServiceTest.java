package com.example.cast.policycompare.service;

import com.example.cast.global.code.GlobalErrorCode;
import com.example.cast.global.exception.BusinessException;
import com.example.cast.integration.ai.client.PolicyComparisonClient;
import com.example.cast.policycompare.code.PolicyComparisonErrorCode;
import com.example.cast.policycompare.dto.response.PolicyComparisonResponse;
import com.example.cast.policycompare.dto.response.PolicyComparisonResponse.ComparisonItem;
import com.example.cast.policycompare.dto.response.PolicyComparisonResponse.EvidenceInfo;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** FastAPI, 운영 DB, Spring 컨텍스트 없이 정책 비교 검토 게이트를 검증한다. */
class PolicyComparisonServiceTest {
    private static final String KEY = "seoul-youth-rent";
    private static final String PREVIOUS = "DOC-2025";
    private static final String CURRENT = "DOC-2026";

    private PolicyComparisonClient client;
    private PolicyComparisonService service;

    @BeforeEach
    void setUp() {
        client = mock(PolicyComparisonClient.class);
        service = new PolicyComparisonService(client);
    }

    @Test
    void validUnapprovedComparisonPreservesComparisonAndPdfEvidence() throws Exception {
        var value = new ObjectMapper().readTree("{\"amount\":200000}");
        var oldEvidence = new EvidenceInfo(PREVIOUS, 3, "2025 official quote", "REVIEWED", null);
        var newEvidence = new EvidenceInfo(CURRENT, 5, "2026 official quote", "SUPPLEMENTAL", value);
        var item = new ComparisonItem("cmp-1", "support amount", "scope-1", "UPDATED",
                "PENDING_REVIEW", "MAIN", "group-1", value, value,
                "REVIEWED", "SUPPLEMENTAL", List.of(oldEvidence), List.of(newEvidence));
        var comparison = comparison(List.of(item), 1, false, false, false);
        when(client.fetchComparison(KEY)).thenReturn(comparison);

        var actual = service.getComparison(KEY);

        assertThat(actual.items()).isEqualTo(comparison.items());
        assertThat(actual.researchImpact().significantChangeCount()).isZero();
        assertThat(actual.reviewStatus()).isEqualTo("REVIEW_REQUIRED");
        assertThat(actual.humanApproved()).isFalse();
        assertThat(actual.javaImportReady()).isFalse();
        assertThat(actual.sourceProvenanceVerified()).isFalse();
        assertThat(actual.items()).hasSize(1);
        assertThat(actual.items().getFirst().previousValue()).isEqualTo(value);
        assertThat(actual.items().getFirst().previousEvidence()).containsExactly(oldEvidence);
        assertThat(actual.items().getFirst().currentEvidence()).containsExactly(newEvidence);
        verify(client).fetchComparison(KEY);
    }

    @Test
    void unknownPolicyIs404WithoutCallingAi() {
        assertThatThrownBy(() -> service.getComparison("unknown-policy"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(GlobalErrorCode.NOT_FOUND));
        verifyNoInteractions(client);
    }

    @Test
    void approvedComparisonIsRejectedAs502() {
        assertBadGateway(comparison(List.of(item("cmp-1", PREVIOUS, CURRENT)), 1,
                true, false, false));
    }

    @Test
    void javaImportReadyComparisonIsRejectedAs502() {
        assertBadGateway(comparison(List.of(item("cmp-1", PREVIOUS, CURRENT)), 1,
                false, true, false));
    }

    @Test
    void verifiedProvenanceFlagIsRejectedAs502() {
        assertBadGateway(comparison(List.of(item("cmp-1", PREVIOUS, CURRENT)), 1,
                false, false, true));
    }

    @Test
    void inconsistentItemCountIsRejectedAs502() {
        assertBadGateway(comparison(List.of(item("cmp-1", PREVIOUS, CURRENT)), 2,
                false, false, false));
    }

    @Test
    void duplicateComparisonIdsAreRejectedAs502() {
        assertBadGateway(comparison(List.of(item("cmp-1", PREVIOUS, CURRENT),
                item("cmp-1", PREVIOUS, CURRENT)), 2, false, false, false));
    }

    @Test
    void evidenceFromAnotherDocumentIsRejectedAs502() {
        assertBadGateway(comparison(List.of(item("cmp-1", "DOC-wrong", CURRENT)), 1,
                false, false, false));
    }

    @Test
    void invalidPdfPageIsRejectedAs502() {
        var invalid = new EvidenceInfo(PREVIOUS, 0, "quote", "REVIEWED", null);
        var source = item("cmp-1", PREVIOUS, CURRENT);
        var item = new ComparisonItem(source.comparisonId(), source.label(), source.scopeId(),
                source.proposedChangeType(), source.reviewStatus(), source.summaryRole(),
                source.changeGroupId(), source.previousValue(), source.currentValue(),
                source.previousSourceType(), source.currentSourceType(),
                List.of(invalid), source.currentEvidence());
        assertBadGateway(comparison(List.of(item), 1, false, false, false));
    }

    @Test
    void impactGroupsPrimaryCandidatesAndKeepsSupportingEvidence() {
        var a = candidate("a", "PRIMARY", "UPDATED", "group-a");
        var b = candidate("b", "PRIMARY", "ADDED_CANDIDATE", "group-a");
        var c = candidate("c", "SUPPORTING", "UPDATED", "group-a");
        var d = candidate("d", "CONTEXT", "ANNUAL_SCHEDULE", "group-b");
        var e = candidate("e", "PRIMARY", "UPDATED", "group-c");
        var all = List.of(a, b, c, d, e);
        when(client.fetchComparison(KEY)).thenReturn(comparison(all, 5, false, false, false));
        var result = service.getComparison(KEY);
        assertThat(result.items()).containsExactlyElementsOf(all);
        assertThat(result.researchImpact().requiresRecheck()).isTrue();
        assertThat(result.researchImpact().significantChangeCount()).isEqualTo(3);
        assertThat(result.researchImpact().changeGroupCount()).isEqualTo(2);
        assertThat(result.researchImpact().groups().getFirst().items()).containsExactly(a, b);
        assertThat(result.researchImpact().groups().getFirst().supportingItems()).containsExactly(c);
        assertThat(result.humanApproved()).isFalse();
    }

    @Test
    void inconsistentUpstreamImpactIsRejected() {
        var item = candidate("a", "PRIMARY", "UPDATED", "group-a");
        var result = new PolicyComparisonResponse(KEY, "Youth rent support", PREVIOUS, CURRENT,
                "REVIEW_REQUIRED", false, false, false, "Unapproved", 1, List.of(item),
                new PolicyComparisonResponse.ResearchImpact(false, 0, 0, List.of()));
        assertBadGateway(result);
    }

    private static ComparisonItem candidate(String id, String role, String type, String group) {
        var source = item(id, PREVIOUS, CURRENT);
        return new ComparisonItem(id, source.label(), source.scopeId(), type, source.reviewStatus(), role,
                group, source.previousValue(), source.currentValue(), source.previousSourceType(),
                source.currentSourceType(), source.previousEvidence(), source.currentEvidence());
    }

    private void assertBadGateway(PolicyComparisonResponse response) {
        when(client.fetchComparison(KEY)).thenReturn(response);
        assertThatThrownBy(() -> service.getComparison(KEY))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode())
                                .isEqualTo(PolicyComparisonErrorCode.AI_BAD_GATEWAY));
    }

    private static ComparisonItem item(String id, String previousDoc, String currentDoc) {
        return new ComparisonItem(id, "amount", "scope-1", "UPDATED", "PENDING_REVIEW",
                "MAIN", "group-1", null, null, "REVIEWED", "SUPPLEMENTAL",
                List.of(new EvidenceInfo(previousDoc, 1, "previous quote", "REVIEWED", null)),
                List.of(new EvidenceInfo(currentDoc, 2, "current quote", "SUPPLEMENTAL", null)));
    }

    private static PolicyComparisonResponse comparison(
            List<ComparisonItem> items, int count, boolean approved,
            boolean importReady, boolean provenanceVerified) {
        return new PolicyComparisonResponse(KEY, "Youth rent support", PREVIOUS, CURRENT,
                "REVIEW_REQUIRED", approved, importReady, provenanceVerified,
                "Unapproved result; consult official sources", count, items);
    }
}
