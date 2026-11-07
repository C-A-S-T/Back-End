package com.example.cast.global.validation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
/** HTTP 이외의 호출도 DTO 제약을 거치도록 서비스 경계에서 사용한다. */
@Component
@RequiredArgsConstructor
public class RequestValidator {
    private final Validator validator;
    public Long requireId(Long id) {
        if (id == null || id <= 0) throw new com.example.cast.global.exception.BusinessException(
                com.example.cast.global.code.GlobalErrorCode.INVALID_REQUEST);
        return id;
    }
    public <T> T validate(T request) {
        if (request == null) throw new com.example.cast.global.exception.BusinessException(
                com.example.cast.global.code.GlobalErrorCode.INVALID_REQUEST);
        var violations = validator.validate(request);
        if (!violations.isEmpty()) throw new ConstraintViolationException(violations);
        return request;
    }
}
