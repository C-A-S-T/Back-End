package com.example.cast.document.service;
import com.example.cast.document.entity.Document;
import com.example.cast.document.repository.DocumentRepository;
import com.example.cast.document.dto.request.*;
import com.example.cast.document.code.DocumentErrorCode;
import com.example.cast.global.exception.BusinessException;
import com.example.cast.global.validation.RequestValidator;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.Instant;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
/** 원문 수집과 문서 입력 검증의 트랜잭션 경계. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DocumentService {
    private final DocumentRepository documents;
    private final RequestValidator validator;
    private final EntityManager em;
    private final Clock clock;

    /** 충돌한 INSERT는 무시하고 다음 SELECT의 새 스냅샷에서 기존 문서를 재사용한다. */
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public Document collect(CollectDocumentRequest request) {
        validator.validate(request);
        String hash = sha256(request.content());
        Instant collectedAt = Instant.now(clock);
        // 예외를 잡고 같은 트랜잭션을 재사용하면 rollback-only가 되므로 ON CONFLICT를 사용한다.
        em.createNativeQuery("""
            INSERT INTO document(doc_title, content_hash, collected_at)
            VALUES (:title, :hash, :collectedAt)
            ON CONFLICT (content_hash) WHERE content_hash IS NOT NULL DO NOTHING
            """)
            .setParameter("title", request.title()).setParameter("hash", hash)
            .setParameter("collectedAt", collectedAt).executeUpdate();
        return documents.findByContentHash(hash).orElseThrow(() ->
                new BusinessException(DocumentErrorCode.DOCUMENT_NOT_FOUND));
    }
    public Document findById(Long id) {
        validator.requireId(id);
        return documents.findById(id).orElseThrow(() -> new BusinessException(DocumentErrorCode.DOCUMENT_NOT_FOUND));
    }
    @Transactional
    public void updateValidity(Long id, DocumentValidityRequest request) {
        validator.validate(request);
        findById(id).updateValidity(request.publishedAt(), request.effectiveFrom(), request.effectiveTo());
    }
    @Transactional
    public void updateTitle(Long id, DocumentTitleRequest request) {
        validator.validate(request);
        findById(id).updateTitle(request.title());
    }
    private static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 is unavailable", e); }
    }
}
