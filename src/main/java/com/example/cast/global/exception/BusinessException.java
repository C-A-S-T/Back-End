package com.example.cast.global.exception;
import com.example.cast.global.code.ErrorCode;
import lombok.Getter;
/** 도메인별 오류 코드를 공통 HTTP 응답으로 전달한다. */
@Getter
public class BusinessException extends RuntimeException {
    private final ErrorCode errorCode;
    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
