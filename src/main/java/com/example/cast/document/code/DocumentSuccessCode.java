package com.example.cast.document.code;
import com.example.cast.global.code.SuccessCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
/** 외부 응답에 사용하는 상태와 메시지. */
@Getter
@RequiredArgsConstructor
public enum DocumentSuccessCode implements SuccessCode {
    DOCUMENT_CREATED(HttpStatus.CREATED, "문서가 생성되었습니다."),
    DOCUMENT_FOUND(HttpStatus.OK, "문서를 조회했습니다.");
    private final HttpStatus status;
    private final String message;
}
