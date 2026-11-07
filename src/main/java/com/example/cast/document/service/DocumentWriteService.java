package com.example.cast.document.service;
import com.example.cast.document.entity.*;
import com.example.cast.document.dto.request.DocumentInputs;
import com.example.cast.global.validation.RequestValidator;
import com.example.cast.global.exception.BusinessException;
import com.example.cast.global.code.GlobalErrorCode;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
/** 내부 쓰기 진입점. HTTP 계층에서는 인증 소유권 확인 후 호출하고 DTO로 변환한다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DocumentWriteService {
    private final EntityManager em;
    private final RequestValidator validator;
    @Transactional
    public DocumentChunk createChunk(Long documentId, DocumentInputs.Chunk input) {
        validator.validate(input);
        DocumentChunk chunk = new DocumentChunk(find(Document.class, documentId), input.index(), input.content());
        em.persist(chunk); return chunk;
    }
    @Transactional
    public void updatePages(Long chunkId, DocumentInputs.PageRange input) {
        validator.validate(input); find(DocumentChunk.class, chunkId).updatePageRange(input.start(), input.end());
    }
    @Transactional
    public void updateSource(Long documentId, DocumentInputs.Source input) {
        validator.validate(input);
        find(Document.class, documentId).updateSourceInformation(input.name(), input.url(), input.path(), input.version(), input.type());
    }
    private <T> T find(Class<T> type, Long id) {
        if (id == null || id <= 0) throw new BusinessException(GlobalErrorCode.INVALID_REQUEST);
        T entity = em.find(type, id);
        if (entity == null) throw new BusinessException(GlobalErrorCode.NOT_FOUND);
        return entity;
    }
}
