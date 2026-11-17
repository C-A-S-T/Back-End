package com.example.cast.source.service;

import com.example.cast.document.entity.Document;
import com.example.cast.global.code.GlobalErrorCode;
import com.example.cast.global.exception.BusinessException;
import com.example.cast.source.dto.response.PolicyChangeDetailResponse;
import com.example.cast.source.dto.response.PolicyChangeDetailResponse.DocumentInfo;
import com.example.cast.source.dto.response.PolicyChangeListResponse;
import com.example.cast.source.dto.response.PolicyChangeListResponse.PolicyChangeItem;
import com.example.cast.source.repository.PolicyChangeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PolicyChangeService {
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_KEYWORD_LENGTH = 100;

    private final PolicyChangeRepository repository;

    public PolicyChangeListResponse list(long userId, String keyword,
            Boolean requiresRecheck, int page, int size) {
        if (userId <= 0 || page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BusinessException(GlobalErrorCode.INVALID_REQUEST);
        }
        String normalizedKeyword = keyword == null ? null : keyword.trim();
        if (normalizedKeyword != null && normalizedKeyword.isEmpty()) {
            normalizedKeyword = null;
        }
        if (normalizedKeyword != null && normalizedKeyword.length() > MAX_KEYWORD_LENGTH) {
            throw new BusinessException(GlobalErrorCode.INVALID_REQUEST);
        }

        var result = repository.findPage(userId, normalizedKeyword,
                requiresRecheck, PageRequest.of(page, size));
        var items = result.getContent().stream().map(row -> {
            var change = row.change();
            var impact = row.impact();
            return new PolicyChangeItem(
                    change.getPolicyChangeId(), change.getPolicyTitle(), change.getCategory(),
                    change.getPreviousDocument().getDocumentId(),
                    change.getPreviousDocument().getDocVersion(),
                    change.getCurrentDocument().getDocumentId(),
                    change.getCurrentDocument().getDocVersion(),
                    change.getChangeSummary(),
                    impact == null ? null : impact.getRequiresRecheck(),
                    impact == null ? null : impact.getImpactSummary(),
                    change.getConfirmedAt());
        }).toList();
        return new PolicyChangeListResponse(items, result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages(), result.hasNext());
    }

    public PolicyChangeDetailResponse getDetail(long userId, Long policyChangeId) {
        if (userId <= 0 || policyChangeId == null || policyChangeId <= 0) {
            throw new BusinessException(GlobalErrorCode.INVALID_REQUEST);
        }

        var row = repository.findDetail(userId, policyChangeId)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND));
        var change = row.change();
        var impact = row.impact();
        return new PolicyChangeDetailResponse(
                change.getPolicyChangeId(),
                change.getPolicyTitle(),
                change.getCategory(),
                change.getChangeSummary(),
                change.getConfirmedAt(),
                toDocumentInfo(change.getPreviousDocument()),
                toDocumentInfo(change.getCurrentDocument()),
                impact == null ? null : impact.getRequiresRecheck(),
                impact == null ? null : impact.getImpactSummary(),
                impact == null ? null : impact.getEvaluatedAt()
        );
    }

    private DocumentInfo toDocumentInfo(Document document) {
        return new DocumentInfo(
                document.getDocumentId(), document.getDocTitle(), document.getDocVersion(),
                document.getSourceName(), document.getSourceUrl(), document.getPublishedAt(),
                document.getEffectiveFrom(), document.getEffectiveTo(),
                document.getSourceType(), document.getReliabilityLevel()
        );
    }
}
