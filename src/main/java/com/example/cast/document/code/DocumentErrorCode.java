package com.example.cast.document.code;
import com.example.cast.global.code.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
/** 외부 응답에 사용하는 상태와 메시지. */
@Getter
@RequiredArgsConstructor
public enum DocumentErrorCode implements ErrorCode {
    DOCUMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "문서를 찾을 수 없습니다."),
    DOCUMENT_PERIOD_INVALID(HttpStatus.BAD_REQUEST, "적용 종료일은 시작일보다 빠를 수 없습니다.");
    private final HttpStatus status;
    private final String message;
}
