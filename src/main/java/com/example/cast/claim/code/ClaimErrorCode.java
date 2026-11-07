package com.example.cast.claim.code;
import com.example.cast.global.code.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
/** 외부 응답에 사용하는 상태와 메시지. */
@Getter
@RequiredArgsConstructor
public enum ClaimErrorCode implements ErrorCode {
    CLAIM_NOT_FOUND(HttpStatus.NOT_FOUND, "검증 대상을 찾을 수 없습니다."),
    CLAIM_ALREADY_VERIFIED(HttpStatus.CONFLICT, "검증이 시작된 내용은 수정할 수 없습니다."),
    VERIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "검증 이력을 찾을 수 없습니다."),
    VERIFICATION_RUN_MISMATCH(HttpStatus.BAD_REQUEST, "검증 대상과 검색 실행이 일치하지 않습니다."),
    INVALID_VERIFICATION_RESULT(HttpStatus.BAD_REQUEST, "검증 결과 조합이 올바르지 않습니다.");
    private final HttpStatus status;
    private final String message;
}
