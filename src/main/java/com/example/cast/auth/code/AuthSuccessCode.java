package com.example.cast.auth.code;

import com.example.cast.global.code.SuccessCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/** 회원가입과 로그인의 공통 응답 상태. */
@Getter
@RequiredArgsConstructor
public enum AuthSuccessCode implements SuccessCode {
    SIGNUP_SUCCESS(HttpStatus.CREATED, "회원가입이 완료되었습니다."),
    LOGIN_SUCCESS(HttpStatus.OK, "로그인에 성공했습니다.");

    private final HttpStatus status;
    private final String message;
}
