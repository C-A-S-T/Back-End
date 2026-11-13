
package com.example.cast.auth.code;

import com.example.cast.global.code.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 인증 실패 시 사용하는 오류 코드.
 *
 * 내부 계정 정보나 자격 증명을 응답에 노출하지 않는다.
 */
@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements ErrorCode {

    EMAIL_ALREADY_EXISTS(
            HttpStatus.CONFLICT,
            "AUTH4091",
            "이미 사용 중인 이메일입니다."
    ),

    INVALID_CREDENTIALS(
            HttpStatus.UNAUTHORIZED,
            "AUTH4011",
            "이메일 또는 비밀번호가 올바르지 않습니다."
    ),

    ACCOUNT_DISABLED(
            HttpStatus.FORBIDDEN,
            "AUTH4031",
            "사용할 수 없는 계정입니다."
    );

    private final HttpStatus status;
    private final String code;
    private final String message;
}
