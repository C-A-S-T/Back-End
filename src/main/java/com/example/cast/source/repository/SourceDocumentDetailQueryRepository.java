package com.example.cast.source.repository;

import com.example.cast.claim.entity.ClaimStatus;
import com.example.cast.claim.entity.Evidence;
import com.example.cast.claim.entity.VerificationStatus;
import com.example.cast.research.entity.AnswerStatus;
import com.example.cast.research.entity.ResearchTaskStatus;
import com.example.cast.source.entity.SentenceScrap;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** 문서 상세 화면 전용 조회. 모든 조회 조건에 문서와 사용자 범위를 명시한다. */
@Repository
@RequiredArgsConstructor
public class SourceDocumentDetailQueryRepository {
    private static final int DETAIL_LIMIT = 20;
    private final EntityManager em;

    @SuppressWarnings("JpaQlInspection")
    private static final String EVIDENCE_FILTER = """
            WHERE e.document.documentId = :documentId
              AND q.user.userId = :userId
              AND t.status = :completedTask
              AND a.answerStatus = :finalAnswer
              AND c.claimStatus = :confirmedClaim
              AND v.status <> :inProgress
              AND v.verAttemptNumber = (
                  SELECT MAX(v2.verAttemptNumber) FROM Verification v2
                  WHERE v2.claim = c AND v2.status <> :inProgress
              )
            """;

    @SuppressWarnings("JpaQlInspection")
    private static final String RELATED_FILTER = """
            WHERE q.user.userId = :userId
              AND (t.taskId = :originTaskId OR (
                  t.status = :completedTask AND EXISTS (
                      SELECT e.evidenceId FROM Evidence e
                      JOIN e.verification v JOIN v.claim c
                      WHERE e.document.documentId = :documentId
                        AND c.answer.task = t
                        AND c.answer.answerStatus = :finalAnswer
                        AND c.claimStatus = :confirmedClaim
                        AND v.status <> :inProgress
                  )
              ))
            """;

    public DetailSection<Evidence> evidences(long userId, long documentId) {
        var count = em.createQuery("""
                SELECT COUNT(e) FROM Evidence e
                JOIN e.verification v JOIN v.claim c
                JOIN c.answer a JOIN a.task t JOIN t.query q
                """ + EVIDENCE_FILTER, Long.class);
        long total = bindEvidence(count, userId, documentId).getSingleResult();
        if (total == 0) return new DetailSection<>(List.of(), 0L, false);

        var result = em.createQuery("""
                SELECT e FROM Evidence e
                JOIN FETCH e.verification v JOIN FETCH v.claim c
                JOIN FETCH c.answer a JOIN FETCH a.task t
                JOIN t.query q
                """ + EVIDENCE_FILTER + " ORDER BY e.evidenceId DESC", Evidence.class);
        List<Evidence> items = bindEvidence(result, userId, documentId)
                .setMaxResults(DETAIL_LIMIT).getResultList();
        return new DetailSection<>(items, total, total > items.size());
    }

    private <T extends Query> T bindEvidence(T query, long userId, long documentId) {
        query.setParameter("documentId", documentId);
        query.setParameter("userId", userId);
        query.setParameter("completedTask", ResearchTaskStatus.COMPLETED);
        query.setParameter("finalAnswer", AnswerStatus.FINAL);
        query.setParameter("confirmedClaim", ClaimStatus.CONFIRMED);
        query.setParameter("inProgress", VerificationStatus.IN_PROGRESS);
        return query;
    }

    /** 최초 저장을 유발한 리서치는 진행 중이어도 포함하되, 다른 연관 리서치는 완료된 것만 표시한다. */
    public DetailSection<ResearchRow> relatedResearches(long userId, long documentId,
            Long originTaskId) {
        var count = em.createQuery("""
                SELECT COUNT(t) FROM ResearchTask t JOIN t.query q
                """ + RELATED_FILTER, Long.class);
        long total = bindRelated(count, userId, documentId, originTaskId).getSingleResult();
        if (total == 0) return new DetailSection<>(List.of(), 0L, false);

        var rows = em.createQuery("""
                SELECT t.taskId, q.researchTitle, q.question, a.summaryContent, t.completedAt
                FROM ResearchTask t JOIN t.query q
                LEFT JOIN Answer a ON a.task = t AND a.answerStatus = :finalAnswer
                """ + RELATED_FILTER +
                " ORDER BY t.completedAt DESC NULLS LAST, t.taskId DESC", Object[].class);
        List<ResearchRow> items = bindRelated(rows, userId, documentId, originTaskId)
                .setMaxResults(DETAIL_LIMIT).getResultList().stream()
                .map(row -> new ResearchRow(
                        (Long) row[0], (String) row[1], (String) row[2],
                        (String) row[3], (java.time.OffsetDateTime) row[4]))
                .toList();
        return new DetailSection<>(items, total, total > items.size());
    }

    private <T extends Query> T bindRelated(T query, long userId, long documentId,
            Long originTaskId) {
        query.setParameter("userId", userId);
        query.setParameter("documentId", documentId);
        query.setParameter("originTaskId", originTaskId);
        query.setParameter("completedTask", ResearchTaskStatus.COMPLETED);
        query.setParameter("finalAnswer", AnswerStatus.FINAL);
        query.setParameter("confirmedClaim", ClaimStatus.CONFIRMED);
        query.setParameter("inProgress", VerificationStatus.IN_PROGRESS);
        return query;
    }

    /** 스크랩은 최신 검증에서 제외된 과거 Evidence도 그대로 보존하여 표시한다. */
    public DetailSection<SentenceScrap> scraps(long userId, long documentId) {
        long total = em.createQuery("""
                SELECT COUNT(s) FROM SentenceScrap s
                WHERE s.user.userId = :userId AND s.evidence.document.documentId = :documentId
                """, Long.class)
                .setParameter("userId", userId).setParameter("documentId", documentId)
                .getSingleResult();
        if (total == 0) return new DetailSection<>(List.of(), 0L, false);

        List<SentenceScrap> items = em.createQuery("""
                SELECT s FROM SentenceScrap s
                JOIN FETCH s.evidence e
                JOIN FETCH e.verification v JOIN FETCH v.claim c
                WHERE s.user.userId = :userId AND e.document.documentId = :documentId
                ORDER BY s.createdAt DESC, s.scrapId DESC
                """, SentenceScrap.class)
                .setParameter("userId", userId).setParameter("documentId", documentId)
                .setMaxResults(DETAIL_LIMIT).getResultList();
        return new DetailSection<>(items, total, total > items.size());
    }

    public record DetailSection<T>(List<T> items, long totalCount, boolean hasMore) { }
    public record ResearchRow(Long taskId, String researchTitle, String question,
                              String summaryContent, java.time.OffsetDateTime completedAt) { }
}
