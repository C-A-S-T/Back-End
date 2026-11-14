package com.example.cast.source.service;

import com.example.cast.claim.entity.Evidence;
import com.example.cast.claim.entity.VerificationStatus;
import com.example.cast.document.entity.SourceType;
import com.example.cast.global.code.GlobalErrorCode;
import com.example.cast.research.entity.AnswerStatus;
import com.example.cast.research.entity.ResearchTaskStatus;
import com.example.cast.global.exception.BusinessException;
import com.example.cast.source.dto.response.SentenceScrapListResponse;
import com.example.cast.source.dto.response.SentenceScrapListResponse.ScrapItem;
import com.example.cast.source.dto.response.SentenceScrapSaveResponse;
import com.example.cast.source.entity.SentenceScrap;
import com.example.cast.source.repository.SentenceScrapQueryRepository;
import com.example.cast.source.repository.SentenceScrapRepository;
import com.example.cast.source.repository.SourceFolderRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SentenceScrapService {
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_KEYWORD_LENGTH = 200;

    private final EntityManager em;
    private final SentenceScrapRepository scraps;
    private final SentenceScrapQueryRepository queryRepository;
    private final SourceFolderRepository folders;

    /** UNIQUE 제약 + ON CONFLICT로 중복 클릭 및 동시 요청에도 최초 저장을 보존한다. */
    @Transactional
    public SaveResult save(Long userId, Long evidenceId) {
        requireId(userId);
        requireId(evidenceId);
        Evidence evidence = em.createQuery("""
                SELECT e FROM Evidence e
                JOIN e.verification v
                JOIN v.claim c
                JOIN c.answer a
                JOIN a.task t
                JOIN t.query q
                WHERE e.evidenceId = :evidenceId AND q.user.userId = :userId
                  AND t.status = :completed
                  AND a.answerStatus = :finalStatus
                  AND v.status <> :inProgress
                """, Evidence.class)
                .setParameter("evidenceId", evidenceId)
                .setParameter("userId", userId)
                .setParameter("completed", ResearchTaskStatus.COMPLETED)
                .setParameter("finalStatus", AnswerStatus.FINAL)
                .setParameter("inProgress", VerificationStatus.IN_PROGRESS)
                .getResultStream().findFirst()
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND));

        List<?> inserted = em.createNativeQuery("""
                INSERT INTO backend.sentence_scrap(user_id, evidence_id, created_at, updated_at)
                VALUES (:userId, :evidenceId, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                ON CONFLICT (user_id, evidence_id) DO NOTHING
                RETURNING scrap_id
                """)
                .setParameter("userId", userId)
                .setParameter("evidenceId", evidence.getEvidenceId())
                .getResultList();
        boolean created = !inserted.isEmpty();
        SentenceScrap saved = created
                ? scraps.findById(((Number) inserted.getFirst()).longValue()).orElseThrow()
                : scraps.findByUser_UserIdAndEvidence_EvidenceId(userId, evidenceId).orElseThrow();
        return new SaveResult(new SentenceScrapSaveResponse(
                saved.getScrapId(), evidenceId, saved.getCreatedAt(), created), created);
    }

    @Transactional
    public void delete(Long userId, Long scrapId) {
        requireId(userId);
        requireId(scrapId);
        SentenceScrap saved = scraps.findByScrapIdAndUser_UserId(scrapId, userId)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND));
        scraps.delete(saved);
    }

    @Transactional(readOnly = true)
    public SentenceScrapListResponse list(Long userId, String keyword,
            VerificationStatus verificationStatus, SourceType sourceType,
            Long folderId, int page, int size) {
        requireId(userId);
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE || (long) page * size > Integer.MAX_VALUE
                || (folderId != null && folderId <= 0)) {
            throw new BusinessException(GlobalErrorCode.INVALID_REQUEST);
        }
        String search = keyword == null || keyword.isBlank() ? null : keyword.trim();
        if (search != null && search.length() > MAX_KEYWORD_LENGTH) {
            throw new BusinessException(GlobalErrorCode.INVALID_REQUEST);
        }
        if (folderId != null &&
                !folders.existsByFolderIdAndUser_UserIdAndFolderCategoryIsNotNull(folderId, userId)) {
            throw new BusinessException(GlobalErrorCode.NOT_FOUND);
        }

        var results = queryRepository.findPage(userId, search, verificationStatus,
                sourceType, folderId, page, size);
        var savedSources = queryRepository.savedSourcesForDocuments(userId, results.items());
        List<ScrapItem> items = results.items().stream().map(scrap -> {
            var evidence = scrap.getEvidence();
            var document = evidence.getDocument();
            var verification = evidence.getVerification();
            var claim = verification.getClaim();
            var saved = savedSources.get(document.getDocumentId());
            return new ScrapItem(scrap.getScrapId(), evidence.getEvidenceId(),
                    evidence.getEvidenceText(), claim.getClaimId(), claim.getClaimContent(),
                    verification.getStatus(), evidence.getEvidenceRelation(),
                    document.getDocumentId(), document.getDocTitle(), document.getSourceName(),
                    document.getSourceUrl(), document.getSourceType(),
                    saved == null ? null : saved.getFolderId(),
                    saved == null ? null : saved.getSavedSourceId(), scrap.getCreatedAt());
        }).toList();
        long total = results.totalElements();
        int totalPages = (int) ((total / size) + (total % size == 0 ? 0 : 1));
        return new SentenceScrapListResponse(items, page, size, total, totalPages,
                ((long) page + 1) < totalPages);
    }

    private static void requireId(Long id) {
        if (id == null || id <= 0) throw new BusinessException(GlobalErrorCode.INVALID_REQUEST);
    }

    public record SaveResult(SentenceScrapSaveResponse response, boolean created) {}
}
