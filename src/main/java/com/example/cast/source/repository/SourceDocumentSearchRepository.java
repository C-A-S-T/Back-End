package com.example.cast.source.repository;

import com.example.cast.document.entity.Document;
import com.example.cast.document.entity.SourceType;
import com.example.cast.source.entity.SaveMethod;
import com.example.cast.source.entity.SavedSource;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

/** 사용자 출처보관함 목록의 검색 및 필터 전용 조회. */
@Repository
@RequiredArgsConstructor
public class SourceDocumentSearchRepository {
    private final EntityManager em;

    public Page<SavedSource> search(Long userId, Long folderId, String keyword,
            SourceType sourceType, SaveMethod saveMethod, Boolean favorite, Pageable pageable) {
        CriteriaBuilder cb = em.getCriteriaBuilder();

        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<SavedSource> countRoot = countQuery.from(SavedSource.class);
        Join<SavedSource, Document> countDocument = countRoot.join("document", JoinType.INNER);
        countQuery.select(cb.count(countRoot)).where(predicates(cb, countRoot, countDocument,
                userId, folderId, keyword, sourceType, saveMethod, favorite));
        long total = em.createQuery(countQuery).getSingleResult();
        if (total == 0 || pageable.getOffset() >= total) {
            return new PageImpl<>(List.of(), pageable, total);
        }

        CriteriaQuery<SavedSource> contentQuery = cb.createQuery(SavedSource.class);
        Root<SavedSource> root = contentQuery.from(SavedSource.class);
        Join<SavedSource, Document> document = root.join("document", JoinType.INNER);
        // 문서 메타데이터는 한 번의 조회로 가져와서 목록 변환 중 N+1을 방지한다.
        root.fetch("document", JoinType.INNER);
        contentQuery.select(root)
                .where(predicates(cb, root, document,
                        userId, folderId, keyword, sourceType, saveMethod, favorite))
                .orderBy(cb.desc(root.get("createdAt")), cb.desc(root.get("savedSourceId")));

        List<SavedSource> items = em.createQuery(contentQuery)
                .setFirstResult((int) pageable.getOffset())
                .setMaxResults(pageable.getPageSize())
                .getResultList();
        return new PageImpl<>(items, pageable, total);
    }

    private Predicate[] predicates(CriteriaBuilder cb, Root<SavedSource> root,
            Join<SavedSource, Document> document, Long userId, Long folderId,
            String keyword, SourceType sourceType, SaveMethod saveMethod, Boolean favorite) {
        List<Predicate> conditions = new ArrayList<>();
        conditions.add(cb.equal(root.get("user").get("userId"), userId));
        if (folderId != null) {
            conditions.add(cb.equal(root.get("folderId"), folderId));
        }
        if (sourceType != null) {
            conditions.add(cb.equal(document.get("sourceType"), sourceType));
        }
        if (saveMethod != null) {
            conditions.add(cb.equal(root.get("saveMethod"), saveMethod));
        }
        if (favorite != null) {
            conditions.add(cb.equal(root.get("favorite"), favorite));
        }
        if (keyword != null) {
            String pattern = "%" + escapeLike(keyword.toLowerCase(Locale.ROOT)) + "%";
            conditions.add(cb.or(
                    cb.like(cb.lower(document.get("docTitle")), pattern, '\\'),
                    cb.like(cb.lower(document.get("sourceName")), pattern, '\\')));
        }
        return conditions.toArray(Predicate[]::new);
    }

    /** LIKE 와일드카드 및 이스케이프 문자를 검색어의 일반 문자로 처리한다. */
    private String escapeLike(String input) {
        return input.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
