package com.example.cast.policycompare.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import tools.jackson.databind.JsonNode;

@Schema(description = "AI가 작성한 정책 비교 결과. 검토 상태가 REVIEW_REQUIRED이면 확정 정책이나 DB 반영 데이터가 아닙니다.")
public record PolicyComparisonResponse(
        String policyKey,
        String policyTitle,
        String previousDocumentKey,
        String currentDocumentKey,
        String reviewStatus,
        boolean humanApproved,
        boolean javaImportReady,
        boolean sourceProvenanceVerified,
        String warning,
        int totalItems,
        List<ComparisonItem> items,
        @Schema(description = "미승인 주요 변경 후보 공통 안내. 개인별 판정이 아님") ResearchImpact researchImpact
) {
    // Source compatibility for existing callers and test fixtures.
    public PolicyComparisonResponse(String policyKey, String policyTitle, String previousDocumentKey,
            String currentDocumentKey, String reviewStatus, boolean humanApproved, boolean javaImportReady,
            boolean sourceProvenanceVerified, String warning, int totalItems, List<ComparisonItem> items) {
        this(policyKey, policyTitle, previousDocumentKey, currentDocumentKey, reviewStatus, humanApproved,
                javaImportReady, sourceProvenanceVerified, warning, totalItems, items, null);
    }

    public record ResearchImpact(
            @Schema(description = "주요 변경 후보 존재 여부. 개인별 지원 자격 판정 아님") boolean requiresRecheck,
            int significantChangeCount,
            int changeGroupCount,
            List<ChangeGroup> groups
    ) { }

    public record ChangeGroup(String changeGroupId, List<ComparisonItem> items,
            List<ComparisonItem> supportingItems) { }

    @Schema(description = "사람의 검토가 필요한 정책 비교 항목")
    public record ComparisonItem(
            String comparisonId,
            String label,
            String scopeId,
            String proposedChangeType,
            String reviewStatus,
            String summaryRole,
            String changeGroupId,
            JsonNode previousValue,
            JsonNode currentValue,
            String previousSourceType,
            String currentSourceType,
            List<EvidenceInfo> previousEvidence,
            List<EvidenceInfo> currentEvidence
    ) { }

    @Schema(description = "원본 PDF에서 인용한 근거. documentId는 Python 문서 키입니다.")
    public record EvidenceInfo(
            String documentId,
            Integer page,
            String quote,
            String source,
            JsonNode evidenceId
    ) { }
}
