package com.example.cast.source.repository;

import com.example.cast.claim.entity.VerificationStatus;
import com.example.cast.document.entity.SourceType;
import com.example.cast.source.entity.SavedSource;
import com.example.cast.source.entity.SentenceScrap;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** to-one fetch join만 사용하여 페이지네이션과 N+1 문제를 방지한다. */
@Repository
@RequiredArgsConstructor
public class SentenceScrapQueryRepository {
    private final EntityManager em;

    public ScrapPage findPage(long userId, String keyword, VerificationStatus verificationStatus,
                              SourceType sourceType, Long folderId, int page, int size) {
        var condition = new StringBuilder(" WHERE s.user.userId = :userId");
        String pattern = null;
        if (keyword != null) {
            pattern = "%" + keyword.toLowerCase(Locale.ROOT)
                    .replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
            condition.append("""
                     AND (LOWER(e.evidenceText) LIKE :pattern ESCAPE '!'
                          OR LOWER(d.docTitle) LIKE :pattern ESCAPE '!'
                          OR LOWER(c.claimContent) LIKE :pattern ESCAPE '!')
                    """);
        }
        if (verificationStatus != null) condition.append(" AND v.status = :verificationStatus");
        if (sourceType != null) condition.append(" AND d.sourceType = :sourceType");
        if (folderId != null) {
            condition.append("""
                     AND EXISTS (SELECT saved.savedSourceId FROM SavedSource saved
                          WHERE saved.user.userId = :userId
                            AND saved.document.documentId = d.documentId
                            AND saved.folderId = :folderId)
                    """);
        }

        String joins = """
                 FROM SentenceScrap s
                 JOIN s.evidence e
                 JOIN e.verification v
                 JOIN v.claim c
                 JOIN e.document d
                """;
        TypedQuery<Long> countQuery = em.createQuery(
                "SELECT COUNT(s)" + joins + condition, Long.class);
        bind(countQuery, userId, pattern, verificationStatus, sourceType, folderId);
        long total = countQuery.getSingleResult();
        if (total == 0) return new ScrapPage(List.of(), 0);

        String fetchJoins = """
                 FROM SentenceScrap s
                 JOIN FETCH s.evidence e
                 JOIN FETCH e.verification v
                 JOIN FETCH v.claim c
                 JOIN FETCH e.document d
                """;
        TypedQuery<SentenceScrap> itemsQuery = em.createQuery(
                "SELECT s" + fetchJoins + condition
                        + " ORDER BY s.createdAt DESC, s.scrapId DESC", SentenceScrap.class);
        bind(itemsQuery, userId, pattern, verificationStatus, sourceType, folderId);
        List<SentenceScrap> items = itemsQuery
                .setFirstResult(page * size).setMaxResults(size).getResultList();
        return new ScrapPage(items, total);
    }

    public Map<Long, SavedSource> savedSourcesForDocuments(long userId, List<SentenceScrap> scraps) {
        if (scraps.isEmpty()) return Map.of();
        var documentIds = scraps.stream().map(s -> s.getEvidence().getDocument().getDocumentId())
                .collect(Collectors.toSet());
        List<SavedSource> sources = em.createQuery("""
                SELECT s FROM SavedSource s WHERE s.user.userId = :userId
                    AND s.document.documentId IN :documentIds
                """, SavedSource.class)
                .setParameter("userId", userId)
                .setParameter("documentIds", documentIds)
                .getResultList();
        Map<Long, SavedSource> byDocument = new HashMap<>();
        for (SavedSource source : sources) {
            byDocument.put(source.getDocument().getDocumentId(), source);
        }
        return byDocument;
    }

    private void bind(Query query, long userId, String pattern, VerificationStatus verificationStatus,
                      SourceType sourceType, Long folderId) {
        query.setParameter("userId", userId);
        if (pattern != null) query.setParameter("pattern", pattern);
        if (verificationStatus != null) query.setParameter("verificationStatus", verificationStatus);
        if (sourceType != null) query.setParameter("sourceType", sourceType);
        if (folderId != null) query.setParameter("folderId", folderId);
    }

    public record ScrapPage(List<SentenceScrap> items, long totalElements) {}
}
