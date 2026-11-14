package com.example.cast.source.service;

import com.example.cast.document.entity.SourceType;
import com.example.cast.global.code.GlobalErrorCode;
import com.example.cast.global.exception.BusinessException;
import com.example.cast.source.dto.response.SourceDocumentDetailResponse;
import com.example.cast.source.dto.response.SourceDocumentDetailResponse.VerificationEvidenceItem;
import com.example.cast.source.dto.response.SourceDocumentDetailResponse.RelatedResearchItem;
import com.example.cast.source.dto.response.SourceDocumentDetailResponse.ScrappedSentenceItem;
import com.example.cast.source.dto.response.SourceDocumentListResponse;
import com.example.cast.source.dto.response.SourceDocumentListResponse.DocumentItem;
import com.example.cast.source.entity.SavedSource;
import com.example.cast.source.entity.SaveMethod;
import com.example.cast.source.entity.SentenceScrap;
import com.example.cast.source.repository.SentenceScrapRepository;
import com.example.cast.source.repository.SourceDocumentDetailQueryRepository;
import com.example.cast.source.repository.SourceDocumentRepository;
import com.example.cast.source.repository.SourceDocumentSearchRepository;
import com.example.cast.source.repository.SourceFolderRepository;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SourceDocumentService {
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_KEYWORD_LENGTH = 100;
    private static final Sort SAVED_ORDER = Sort.by(
            Sort.Order.desc("createdAt"), Sort.Order.desc("savedSourceId"));

    private final SourceDocumentRepository documentRepository;
    private final SourceDocumentSearchRepository searchRepository;
    private final SourceDocumentDetailQueryRepository detailQuery;
    private final SentenceScrapRepository scrapRepository;
    private final SourceFolderRepository folderRepository;

    public SourceDocumentListResponse listDocuments(Long userId, Long folderId,
            String keyword, SourceType sourceType, SaveMethod saveMethod, Boolean favorite,
            int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE || (folderId != null && folderId <= 0)) {
            throw new BusinessException(GlobalErrorCode.INVALID_REQUEST);
        }
        String normalizedKeyword = keyword == null ? null : keyword.trim();
        if (normalizedKeyword != null && normalizedKeyword.isEmpty()) {
            normalizedKeyword = null;
        }
        if (normalizedKeyword != null && normalizedKeyword.length() > MAX_KEYWORD_LENGTH) {
            throw new BusinessException(GlobalErrorCode.INVALID_REQUEST);
        }
        if (folderId != null &&
                !folderRepository.existsByFolderIdAndUser_UserIdAndFolderCategoryIsNotNull(folderId, userId)) {
            // Do not reveal whether a folder belongs to another user.
            throw new BusinessException(GlobalErrorCode.NOT_FOUND);
        }

        var pageable = PageRequest.of(page, size, SAVED_ORDER);
        Page<SavedSource> saved = searchRepository.search(userId, folderId,
                normalizedKeyword, sourceType, saveMethod, favorite, pageable);

        return new SourceDocumentListResponse(
                saved.getContent().stream().map(this::toItem).toList(),
                saved.getNumber(), saved.getSize(), saved.getTotalElements(),
                saved.getTotalPages(), saved.hasNext()
        );
    }

    public SourceDocumentDetailResponse getDocumentDetail(Long userId, Long savedSourceId) {
        if (savedSourceId == null || savedSourceId <= 0) {
            throw new BusinessException(GlobalErrorCode.INVALID_REQUEST);
        }

        SavedSource saved = documentRepository.findBySavedSourceIdAndUser_UserId(savedSourceId, userId)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND));
        var document = saved.getDocument();
        var originTask = saved.getOriginTask();
        Long originTaskId = originTask == null ? null : originTask.getTaskId();

        var evidenceSection = detailQuery.evidences(userId, document.getDocumentId());
        var relatedSection = detailQuery.relatedResearches(userId, document.getDocumentId(), originTaskId);
        var scrapSection = detailQuery.scraps(userId, document.getDocumentId());

        var evidenceIds = evidenceSection.items().stream()
                .map(e -> e.getEvidenceId()).toList();
        Map<Long, SentenceScrap> scrapByEvidence = evidenceIds.isEmpty()
                ? Map.of()
                : scrapRepository.findByUser_UserIdAndEvidence_EvidenceIdIn(userId, evidenceIds)
                        .stream().collect(Collectors.toMap(
                                s -> s.getEvidence().getEvidenceId(), Function.identity()));

        var evidenceItems = evidenceSection.items().stream().map(e -> {
            var verification = e.getVerification();
            var claim = verification.getClaim();
            var scrap = scrapByEvidence.get(e.getEvidenceId());
            return new VerificationEvidenceItem(
                    e.getEvidenceId(), e.getEvidenceText(), e.getEvidenceRelation(),
                    claim.getClaimId(), claim.getClaimContent(), verification.getStatus(),
                    claim.getAnswer().getTask().getTaskId(), e.getPageStart(), e.getPageEnd(),
                    e.getSectionTitle(), scrap != null,
                    scrap == null ? null : scrap.getScrapId());
        }).toList();
        var relatedItems = relatedSection.items().stream()
                .map(row -> new RelatedResearchItem(row.taskId(), row.researchTitle(),
                        row.question(), row.summaryContent(), row.completedAt()))
                .toList();
        var scrapItems = scrapSection.items().stream().map(s -> {
            var evidence = s.getEvidence();
            return new ScrappedSentenceItem(s.getScrapId(), evidence.getEvidenceId(),
                    evidence.getEvidenceText(), evidence.getVerification().getClaim().getClaimContent(),
                    s.getCreatedAt());
        }).toList();

        return new SourceDocumentDetailResponse(
                saved.getSavedSourceId(),
                document.getDocumentId(),
                document.getDocTitle(),
                document.getSourceName(),
                document.getSourceUrl(),
                document.getPublishedAt(),
                document.getEffectiveFrom(),
                document.getEffectiveTo(),
                document.getDocVersion(),
                document.getSourceType(),
                document.getReliabilityLevel(),
                saved.getSaveMethod(),
                saved.getFolderId(),
                saved.isFavorite(),
                originTaskId,
                saved.getCreatedAt(),
                evidenceItems, evidenceSection.totalCount(), evidenceSection.hasMore(),
                relatedItems, relatedSection.totalCount(), relatedSection.hasMore(),
                scrapItems, scrapSection.totalCount(), scrapSection.hasMore()
        );
    }

    private DocumentItem toItem(SavedSource saved) {
        var document = saved.getDocument();
        return new DocumentItem(
                saved.getSavedSourceId(),
                document.getDocumentId(),
                document.getDocTitle(),
                document.getSourceName(),
                document.getSourceUrl(),
                document.getPublishedAt(),
                document.getSourceType(),
                document.getReliabilityLevel(),
                saved.getSaveMethod(),
                saved.getFolderId(),
                saved.isFavorite(),
                saved.getCreatedAt()
        );
    }
}
