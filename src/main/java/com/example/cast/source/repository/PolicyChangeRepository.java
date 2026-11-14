package com.example.cast.source.repository;

import com.example.cast.source.entity.PolicyChange;
import com.example.cast.source.entity.UserPolicyChangeImpact;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

/** Global policy changes, with only the requesting user's impact joined. */
@Repository
@RequiredArgsConstructor
public class PolicyChangeRepository {
    private final EntityManager em;

    public Page<PolicyChangeRow> findPage(long userId, String keyword,
            Boolean requiresRecheck, Pageable pageable) {
        String condition = " WHERE 1 = 1";
        String pattern = null;
        if (keyword != null) {
            pattern = "%" + escapeLike(keyword.toLowerCase(Locale.ROOT)) + "%";
            condition += " AND LOWER(p.policyTitle) LIKE :keyword ESCAPE '!'";
        }
        if (requiresRecheck != null) {
            condition += " AND i.requiresRecheck = :requiresRecheck";
        }

        String from = """
                 FROM PolicyChange p
                 LEFT JOIN p.impacts i ON i.user.userId = :userId
                """;
        var countQuery = em.createQuery("SELECT COUNT(p)" + from + condition, Long.class);
        bind(countQuery, userId, pattern, requiresRecheck);
        long total = countQuery.getSingleResult();
        if (total == 0 || pageable.getOffset() >= total) {
            return new PageImpl<>(List.of(), pageable, total);
        }

        // The two document associations are to-one: fetch joins are safe for pagination.
        String contentFrom = """
                 FROM PolicyChange p
                 JOIN FETCH p.previousDocument
                 JOIN FETCH p.currentDocument
                 LEFT JOIN p.impacts i ON i.user.userId = :userId
                """;
        var contentQuery = em.createQuery("SELECT p, i" + contentFrom + condition
                + " ORDER BY p.confirmedAt DESC, p.policyChangeId DESC", Object[].class);
        bind(contentQuery, userId, pattern, requiresRecheck);
        List<PolicyChangeRow> items = contentQuery
                .setFirstResult((int) pageable.getOffset())
                .setMaxResults(pageable.getPageSize())
                .getResultList().stream()
                .map(row -> new PolicyChangeRow((PolicyChange) row[0],
                        (UserPolicyChangeImpact) row[1]))
                .toList();
        return new PageImpl<>(items, pageable, total);
    }

    private void bind(Query query, long userId, String pattern, Boolean requiresRecheck) {
        query.setParameter("userId", userId);
        if (pattern != null) query.setParameter("keyword", pattern);
        if (requiresRecheck != null) query.setParameter("requiresRecheck", requiresRecheck);
    }

    /** ! is the explicit JPQL LIKE escape character. */
    private String escapeLike(String value) {
        return value.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    public record PolicyChangeRow(PolicyChange change, UserPolicyChangeImpact impact) { }
}
