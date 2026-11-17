package com.example.cast.research.service;

import com.example.cast.claim.entity.Claim;
import com.example.cast.claim.entity.ClaimStatus;
import com.example.cast.claim.entity.Evidence;
import com.example.cast.claim.entity.Verification;
import com.example.cast.claim.entity.VerificationStatus;
import com.example.cast.global.code.GlobalErrorCode;
import com.example.cast.global.exception.BusinessException;
import com.example.cast.research.dto.response.ResearchEvidenceResponse;
import com.example.cast.research.dto.response.ResearchEvidenceResponse.ClaimItem;
import com.example.cast.research.dto.response.ResearchEvidenceResponse.EvidenceItem;
import com.example.cast.research.entity.AnswerStatus;
import com.example.cast.research.entity.ResearchTask;
import com.example.cast.research.entity.ResearchTaskStatus;
import com.example.cast.source.entity.SentenceScrap;
import com.example.cast.source.repository.SentenceScrapRepository;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ResearchEvidenceService {
    private final EntityManager em;
    private final SentenceScrapRepository scraps;

    /** 완료된 주장별 가장 높은 검증 시도 번호의 근거만 리서치 화면에 보여준다. */
    public ResearchEvidenceResponse getEvidences(Long userId, Long taskId) {
        if (userId == null || userId <= 0 || taskId == null || taskId <= 0) {
            throw new BusinessException(GlobalErrorCode.INVALID_REQUEST);
        }
        ResearchTask task = em.createQuery("""
                SELECT t FROM ResearchTask t
                WHERE t.taskId = :taskId AND t.query.user.userId = :userId
                """, ResearchTask.class)
                .setParameter("taskId", taskId).setParameter("userId", userId)
                .getResultStream().findFirst()
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND));
        if (task.getStatus() != ResearchTaskStatus.COMPLETED) {
            return new ResearchEvidenceResponse(taskId, List.of());
        }

        List<Claim> claims = em.createQuery("""
                SELECT c FROM Claim c JOIN c.answer a
                WHERE a.task.taskId = :taskId
                  AND a.answerStatus = :finalStatus AND c.claimStatus = :confirmed
                ORDER BY c.claimIndex ASC, c.claimId ASC
                """, Claim.class)
                .setParameter("taskId", taskId)
                .setParameter("finalStatus", AnswerStatus.FINAL)
                .setParameter("confirmed", ClaimStatus.CONFIRMED)
                .getResultList();
        if (claims.isEmpty()) return new ResearchEvidenceResponse(taskId, List.of());

        List<Verification> attempts = em.createQuery("""
                SELECT v FROM Verification v JOIN FETCH v.claim c
                WHERE c.answer.task.taskId = :taskId AND v.status <> :inProgress
                ORDER BY c.claimIndex ASC, v.verAttemptNumber DESC, v.verificationId DESC
                """, Verification.class)
                .setParameter("taskId", taskId)
                .setParameter("inProgress", VerificationStatus.IN_PROGRESS)
                .getResultList();
        Map<Long, Verification> latest = new HashMap<>();
        for (Verification attempt : attempts) {
            latest.putIfAbsent(attempt.getClaim().getClaimId(), attempt);
        }
        if (latest.isEmpty()) {
            return new ResearchEvidenceResponse(taskId, claims.stream()
                    .map(c -> new ClaimItem(c.getClaimId(), c.getClaimIndex(), c.getClaimContent(),
                            null, List.of())).toList());
        }

        List<Long> verificationIds = latest.values().stream()
                .map(Verification::getVerificationId).toList();
        List<Evidence> evidences = em.createQuery("""
                SELECT e FROM Evidence e JOIN FETCH e.document
                WHERE e.verification.verificationId IN :verificationIds ORDER BY e.evidenceId ASC
                """, Evidence.class)
                .setParameter("verificationIds", verificationIds)
                .getResultList();
        Map<Long, List<Evidence>> byVerification = evidences.stream()
                .collect(Collectors.groupingBy(e -> e.getVerification().getVerificationId()));
        Map<Long, SentenceScrap> scrapsByEvidence = new HashMap<>();
        if (!evidences.isEmpty()) {
            var evidenceIds = evidences.stream().map(Evidence::getEvidenceId).toList();
            for (SentenceScrap scrap : scraps.findByUser_UserIdAndEvidence_EvidenceIdIn(userId, evidenceIds)) {
                scrapsByEvidence.put(scrap.getEvidence().getEvidenceId(), scrap);
            }
        }

        List<ClaimItem> response = new ArrayList<>();
        for (Claim claim : claims) {
            Verification verification = latest.get(claim.getClaimId());
            if (verification == null) {
                response.add(new ClaimItem(claim.getClaimId(), claim.getClaimIndex(),
                        claim.getClaimContent(), null, List.of()));
                continue;
            }
            List<EvidenceItem> items = byVerification
                    .getOrDefault(verification.getVerificationId(), List.of()).stream().map(e -> {
                        var document = e.getDocument();
                        var scrap = scrapsByEvidence.get(e.getEvidenceId());
                        return new EvidenceItem(e.getEvidenceId(), e.getEvidenceText(),
                                e.getEvidenceRelation(), document.getDocumentId(),
                                document.getDocTitle(), document.getSourceName(),
                                document.getSourceUrl(), document.getSourceType(),
                                e.getPageStart(), e.getPageEnd(), e.getSectionTitle(),
                                scrap != null, scrap == null ? null : scrap.getScrapId());
                    }).toList();
            response.add(new ClaimItem(claim.getClaimId(), claim.getClaimIndex(),
                    claim.getClaimContent(), verification.getStatus(), items));
        }
        return new ResearchEvidenceResponse(taskId, response);
    }
}
