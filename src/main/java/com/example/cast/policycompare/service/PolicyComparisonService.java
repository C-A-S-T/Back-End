package com.example.cast.policycompare.service;

import com.example.cast.global.code.GlobalErrorCode;
import com.example.cast.global.exception.BusinessException;
import com.example.cast.integration.ai.client.PolicyComparisonClient;
import com.example.cast.policycompare.code.PolicyComparisonErrorCode;
import com.example.cast.policycompare.dto.response.PolicyComparisonResponse;
import com.example.cast.policycompare.dto.response.PolicyComparisonResponse.ComparisonItem;
import com.example.cast.policycompare.dto.response.PolicyComparisonResponse.EvidenceInfo;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** AI의 정책 비교 결과를 조회한다. 검토 중인 결과는 미승인 상태를 유지하며 DB에 저장하지 않는다. */
@Service
@RequiredArgsConstructor
public class PolicyComparisonService {
    private static final String SUPPORTED_POLICY = "seoul-youth-rent";
    private static final int MAX_ITEMS = 1_000;
    private static final int MAX_EVIDENCE_PER_SIDE = 1_000;

    private final PolicyComparisonClient client;

    public PolicyComparisonResponse getComparison(String policyKey) {
        if (!SUPPORTED_POLICY.equals(policyKey)) {
            throw new BusinessException(GlobalErrorCode.NOT_FOUND);
        }
        var response = client.fetchComparison(policyKey);
        validateComparison(policyKey, response);
        return response;
    }

    private void validateComparison(String requestedKey, PolicyComparisonResponse result) {
        if (result == null || !requestedKey.equals(result.policyKey())
                || !"REVIEW_REQUIRED".equals(result.reviewStatus())
                || result.humanApproved() || result.javaImportReady()
                || result.sourceProvenanceVerified()
                || isBlank(result.policyTitle()) || isBlank(result.warning())
                || isBlank(result.previousDocumentKey()) || isBlank(result.currentDocumentKey())
                || result.previousDocumentKey().equals(result.currentDocumentKey())
                || result.items() == null || result.items().size() > MAX_ITEMS
                || result.totalItems() != result.items().size()) {
            invalidResponse();
        }

        Set<String> ids = new HashSet<>();
        for (ComparisonItem item : result.items()) {
            if (item == null || isBlank(item.comparisonId()) || !ids.add(item.comparisonId())
                    || isBlank(item.label()) || !"PENDING_REVIEW".equals(item.reviewStatus())) {
                invalidResponse();
            }
            validateEvidence(item.previousEvidence(), result.previousDocumentKey());
            validateEvidence(item.currentEvidence(), result.currentDocumentKey());
        }
    }

    private void validateEvidence(List<EvidenceInfo> evidence, String expectedDocumentKey) {
        if (evidence == null || evidence.size() > MAX_EVIDENCE_PER_SIDE) {
            invalidResponse();
        }
        for (EvidenceInfo item : evidence) {
            if (item == null || !expectedDocumentKey.equals(item.documentId())
                    || (item.page() != null && item.page() < 1)
                    || isBlank(item.quote())
                    || !("REVIEWED".equals(item.source()) || "SUPPLEMENTAL".equals(item.source()))) {
                invalidResponse();
            }
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private void invalidResponse() {
        throw new BusinessException(PolicyComparisonErrorCode.AI_BAD_GATEWAY);
    }
}
