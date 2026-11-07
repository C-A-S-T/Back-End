package com.example.cast.retrieval.code;
import com.example.cast.global.code.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
/** 외부 응답에 사용하는 상태와 메시지. */
@Getter
@RequiredArgsConstructor
public enum RetrievalErrorCode implements ErrorCode {
    CLAIM_TASK_MISMATCH(HttpStatus.BAD_REQUEST, "검증 대상과 조사 작업이 일치하지 않습니다.");
    private final HttpStatus status;
    private final String message;
}
