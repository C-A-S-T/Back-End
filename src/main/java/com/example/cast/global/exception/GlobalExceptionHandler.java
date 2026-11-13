
package com.example.cast.global.exception;

import com.example.cast.global.code.GlobalErrorCode;
import com.example.cast.global.response.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Map;

/**
 * 공통 API 예외 처리기.
 *
 * 내부 예외 메시지와 민감한 정보는 외부 응답에 노출하지 않는다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 비즈니스 예외.
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> business(
            BusinessException e
    ) {
        return ApiResponse.error(e.getErrorCode());
    }

    /**
     * 입력값 및 요청 파라미터 검증 실패.
     */
    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            ConstraintViolationException.class,
            ServletRequestBindingException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ApiResponse<Map<String, Object>>> validation(
            Exception e
    ) {
        return ApiResponse.error(
                GlobalErrorCode.INVALID_REQUEST
        );
    }

    /**
     * 메서드 검증 실패.
     *
     * 응답 반환값 검증 실패는 서버 내부 오류로 처리한다.
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> methodValidation(
            HandlerMethodValidationException e
    ) {
        if (e.isForReturnValue()) {
            return unexpected(e);
        }

        return ApiResponse.error(
                GlobalErrorCode.INVALID_REQUEST
        );
    }

    /**
     * JSON 요청 본문 파싱 실패.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> json(
            HttpMessageNotReadableException e
    ) {
        return ApiResponse.error(
                GlobalErrorCode.MALFORMED_JSON
        );
    }

    /**
     * 데이터베이스 제약조건 위반.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> conflict(
            DataIntegrityViolationException e
    ) {
        log.warn("Data integrity violation", e);

        return ApiResponse.error(
                GlobalErrorCode.DATA_CONFLICT
        );
    }

    /**
     * 존재하지 않는 경로 또는 리소스.
     */
    @ExceptionHandler({
            NoResourceFoundException.class,
            NoHandlerFoundException.class
    })
    public ResponseEntity<ApiResponse<Map<String, Object>>> notFound(
            Exception e
    ) {
        return ApiResponse.error(
                GlobalErrorCode.NOT_FOUND
        );
    }

    /**
     * 지원하지 않는 HTTP 메서드.
     *
     * Allow 등 기존 응답 헤더를 유지한다.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> method(
            HttpRequestMethodNotSupportedException e
    ) {
        var response = ApiResponse.error(
                GlobalErrorCode.METHOD_NOT_ALLOWED
        );

        return ResponseEntity
                .status(response.getStatusCode())
                .headers(e.getHeaders())
                .body(response.getBody());
    }

    /**
     * 지원하지 않는 요청 Content-Type.
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> media(
            HttpMediaTypeNotSupportedException e
    ) {
        return ApiResponse.error(
                GlobalErrorCode.UNSUPPORTED_MEDIA_TYPE
        );
    }

    /**
     * 지원하지 않는 Accept 타입.
     */
    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> accept(
            HttpMediaTypeNotAcceptableException e
    ) {
        return ApiResponse.error(
                GlobalErrorCode.NOT_ACCEPTABLE
        );
    }

    /**
     * 처리되지 않은 서버 내부 오류.
     *
     * 상세 오류는 로그에만 기록한다.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> unexpected(
            Exception e
    ) {
        log.error("Unexpected server error", e);

        return ApiResponse.error(
                GlobalErrorCode.INTERNAL_ERROR
        );
    }
}
