package com.example.cast.source.repository;

import com.example.cast.source.entity.SourceFolder;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SourceFolderRepository extends JpaRepository<SourceFolder, Long> {
    boolean existsByFolderIdAndUser_UserIdAndFolderCategoryIsNotNull(Long folderId, Long userId);
}
