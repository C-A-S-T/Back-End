package com.example.cast.global.exception;
import com.example.cast.global.code.GlobalErrorCode;
import com.example.cast.global.response.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
/** 입력값과 내부 예외 메시지를 응답에 그대로 노출하지 않는다. */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> business(BusinessException e) {
        return ApiResponse.error(e.getErrorCode());
    }
    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class,
            ServletRequestBindingException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiResponse<Void>> validation(Exception e) {
        return ApiResponse.error(GlobalErrorCode.INVALID_REQUEST);
    }
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<Void>> methodValidation(HandlerMethodValidationException e) {
        if (e.isForReturnValue()) return unexpected(e);
        return ApiResponse.error(GlobalErrorCode.INVALID_REQUEST);
    }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> json(HttpMessageNotReadableException e) {
        return ApiResponse.error(GlobalErrorCode.MALFORMED_JSON);
    }
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> conflict(DataIntegrityViolationException e) {
        log.warn("Data integrity violation", e);
        return ApiResponse.error(GlobalErrorCode.DATA_CONFLICT);
    }
    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<ApiResponse<Void>> notFound(Exception e) {
        return ApiResponse.error(GlobalErrorCode.NOT_FOUND);
    }
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> method(HttpRequestMethodNotSupportedException e) {
        var response = ApiResponse.error(GlobalErrorCode.METHOD_NOT_ALLOWED);
        return ResponseEntity.status(response.getStatusCode()).headers(e.getHeaders()).body(response.getBody());
    }
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> media(HttpMediaTypeNotSupportedException e) {
        return ApiResponse.error(GlobalErrorCode.UNSUPPORTED_MEDIA_TYPE);
    }
    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ApiResponse<Void>> accept(HttpMediaTypeNotAcceptableException e) {
        return ApiResponse.error(GlobalErrorCode.NOT_ACCEPTABLE);
    }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> unexpected(Exception e) {
        log.error("Unexpected server error", e);
        return ApiResponse.error(GlobalErrorCode.INTERNAL_ERROR);
    }
}
