package com.example.cast.auth.code;

import com.example.cast.global.code.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/** 인증 실패 시 내부 계정 정보나 자격 증명을 노출하지 않는 응답 코드. */
@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements ErrorCode {
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
    ACCOUNT_DISABLED(HttpStatus.FORBIDDEN, "사용할 수 없는 계정입니다.");

    private final HttpStatus status;
    private final String message;
}
