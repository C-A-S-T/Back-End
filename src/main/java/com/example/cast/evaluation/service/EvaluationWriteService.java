package com.example.cast.evaluation.service;
import com.example.cast.evaluation.entity.*;
import com.example.cast.evaluation.dto.request.EvaluationInputs;
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
public class EvaluationWriteService {
    private final EntityManager em;
    private final RequestValidator validator;
    @Transactional
    public EvaluationCase create(EvaluationInputs.Create input) {
        validator.validate(input);
        for (Long id : input.chunkIds()) find(com.example.cast.document.entity.DocumentChunk.class, id);
        var expected = input.claims().stream().map(c -> new ExpectedClaim(c.type(), c.content(), java.util.Map.copyOf(c.value()))).toList();
        EvaluationCase evaluation = new EvaluationCase(input.question(), input.referenceAnswer(), expected, input.chunkIds(), input.category());
        em.persist(evaluation); return evaluation;
    }
    private <T> T find(Class<T> type, Long id) {
        if (id == null || id <= 0) throw new BusinessException(GlobalErrorCode.INVALID_REQUEST);
        T entity = em.find(type, id);
        if (entity == null) throw new BusinessException(GlobalErrorCode.NOT_FOUND);
        return entity;
    }
}
