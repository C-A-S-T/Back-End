package com.example.cast.evaluation.service;
import com.example.cast.evaluation.entity.EvaluationCase;
import com.example.cast.document.repository.DocumentChunkRepository;
import com.example.cast.global.code.GlobalErrorCode;
import com.example.cast.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
/** 평가 실행 전 참조를 확인한다. 재청킹 시 기존 ID를 추측하여 대체하지 않는다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EvaluationReferenceService {
    private final DocumentChunkRepository chunks;
    public void validateForStructuredEvaluation(EvaluationCase evaluation) {
        if (evaluation.getExpectedClaims().stream().anyMatch(c -> c == null || c.type() == null
                || c.content() == null || c.content().isBlank() || c.value() == null || c.value().isEmpty()))
            throw new BusinessException(GlobalErrorCode.INVALID_REQUEST);
        for (Long id : evaluation.getRelevantChunkIds()) {
            if (id == null || !chunks.existsById(id)) throw new BusinessException(GlobalErrorCode.NOT_FOUND);
        }
    }
}
