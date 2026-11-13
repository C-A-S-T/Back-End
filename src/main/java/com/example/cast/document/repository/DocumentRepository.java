package com.example.cast.document.repository;
import com.example.cast.document.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
/** 백엔드 소유 출처 스냅샷만 조회한다. */
public interface DocumentRepository extends JpaRepository<Document, Long> {
    Optional<Document> findByAiDocumentId(Long aiDocumentId);
}
