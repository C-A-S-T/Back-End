package com.example.cast.source.repository;

import com.example.cast.source.entity.SourceFolderCategory;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class SourceOverviewRepository {
    private final EntityManager em;

    public SavedCounts countSavedSources(long userId) {
        Object[] row = (Object[]) em.createNativeQuery("""
                SELECT COUNT(s.saved_source_id),
                       COUNT(s.saved_source_id) FILTER (WHERE s.save_method = 'AUTO'),
                       COUNT(s.saved_source_id) FILTER (WHERE s.folder_id IS NULL),
                       COUNT(s.saved_source_id) FILTER (
                           WHERE s.folder_id IS NOT NULL AND f.folder_category IS NULL
                       )
                FROM backend.saved_source s
                LEFT JOIN backend.source_folder f
                  ON f.user_id = s.user_id AND f.folder_id = s.folder_id
                WHERE s.user_id = :userId
                """)
                .setParameter("userId", userId)
                .getSingleResult();

        return new SavedCounts(
                ((Number) row[0]).longValue(),
                ((Number) row[1]).longValue(),
                ((Number) row[2]).longValue(),
                ((Number) row[3]).longValue()
        );
    }

    public List<FolderCounts> countFixedFolders(long userId) {
        @SuppressWarnings("JpaQueryApiInspection") List<?> rows = em.createNativeQuery("""
                SELECT f.folder_id, f.folder_category, f.folder_name,
                       COUNT(s.saved_source_id)
                FROM backend.source_folder f
                LEFT JOIN backend.saved_source s
                  ON s.user_id = f.user_id AND s.folder_id = f.folder_id
                WHERE f.user_id = :userId
                  AND f.folder_category IN ('HOUSING', 'GOV_SUPPORT', 'EDUCATION')
                GROUP BY f.folder_id, f.folder_category, f.folder_name
                ORDER BY CASE f.folder_category
                    WHEN 'HOUSING' THEN 1
                    WHEN 'GOV_SUPPORT' THEN 2
                    WHEN 'EDUCATION' THEN 3
                    ELSE 4 END
                """)
                .setParameter("userId", userId)
                .getResultList();

        return rows.stream().map(result -> {
            Object[] row = (Object[]) result;
            return new FolderCounts(
                    ((Number) row[0]).longValue(),
                    SourceFolderCategory.valueOf((String) row[1]),
                    (String) row[2],
                    ((Number) row[3]).longValue()
            );
        }).toList();
    }

    public record SavedCounts(
            long total,
            long automatic,
            long unclassified,
            long legacyFolder
    ) { }

    public record FolderCounts(
            long folderId,
            SourceFolderCategory category,
            String folderName,
            long documentCount
    ) { }
}
