package com.example.cast.source.repository;

import com.example.cast.source.entity.SavedSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SourceDocumentRepository extends JpaRepository<SavedSource, Long> {
    @EntityGraph(attributePaths = "document")
    Page<SavedSource> findByUser_UserId(Long userId, Pageable pageable);

    @EntityGraph(attributePaths = "document")
    Page<SavedSource> findByUser_UserIdAndFolderId(Long userId, Long folderId, Pageable pageable);
}
