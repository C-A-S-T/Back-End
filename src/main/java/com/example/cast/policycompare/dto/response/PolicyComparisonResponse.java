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
        List<ComparisonItem> items
) {
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
