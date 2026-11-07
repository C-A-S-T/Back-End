package com.example.cast.document.repository;
import com.example.cast.document.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;
/** Document 영속성 접근. 변경 정책은 서비스에서 수행한다. */
public interface DocumentRepository extends JpaRepository<Document, Long> {
    java.util.Optional<Document> findByContentHash(String contentHash);
}
