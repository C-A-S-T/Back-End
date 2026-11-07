package com.example.cast.claim.service;
import com.example.cast.claim.entity.*;
import com.example.cast.claim.dto.request.ClaimInputs;
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
public class ClaimWriteService {
    private final EntityManager em;
    private final RequestValidator validator;
    @Transactional
    public Evidence addEvidence(Long verificationId, Long chunkId, ClaimInputs.EvidenceInput input) {
        validator.validate(input);
        Evidence evidence = new Evidence(find(Verification.class, verificationId),
                find(com.example.cast.document.entity.DocumentChunk.class, chunkId), input.text(), input.relation());
        em.persist(evidence); return evidence;
    }
    private <T> T find(Class<T> type, Long id) {
        if (id == null || id <= 0) throw new BusinessException(GlobalErrorCode.INVALID_REQUEST);
        T entity = em.find(type, id);
        if (entity == null) throw new BusinessException(GlobalErrorCode.NOT_FOUND);
        return entity;
    }
}
