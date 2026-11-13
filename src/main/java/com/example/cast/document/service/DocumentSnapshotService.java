package com.example.cast.document.service;
import com.example.cast.document.dto.SourceSnapshot;
import com.example.cast.document.entity.Document;
import com.example.cast.document.repository.DocumentRepository;
import com.example.cast.global.validation.RequestValidator;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
/** 동시 결과 수신에서도 출처 스냅샷을 중복 생성하지 않는다. */
@Service
@RequiredArgsConstructor
public class DocumentSnapshotService {
    private final EntityManager em;
    private final DocumentRepository documents;
    private final RequestValidator validator;
    @Transactional
    public Document capture(SourceSnapshot source) {
        validator.validate(source);
        em.createNativeQuery("""
            INSERT INTO backend.document(ai_document_id, doc_title, source_name, source_url,
                published_at, effective_from, effective_to, doc_version, source_type, reliability_level)
            VALUES (:id, :title, :name, :url, :published, :fromDate, :toDate, :version, :type, :reliability)
            ON CONFLICT (ai_document_id) DO NOTHING
            """).setParameter("id", source.aiDocumentId()).setParameter("title", source.title())
            .setParameter("name", source.sourceName()).setParameter("url", source.sourceUrl())
            .setParameter("published", source.publishedAt()).setParameter("fromDate", source.effectiveFrom())
            .setParameter("toDate", source.effectiveTo()).setParameter("version", source.version())
            .setParameter("type", source.sourceType().name())
            .setParameter("reliability", source.reliabilityLevel() == null ? null : source.reliabilityLevel().name())
            .executeUpdate();
        return documents.findByAiDocumentId(source.aiDocumentId()).orElseThrow();
    }
}
