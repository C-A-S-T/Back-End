package com.example.cast.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 로그인도 BCrypt 입력 한도를 적용하지만 기존 계정에 가입 최소 길이를 소급하지 않는다. */
public record LoginRequest(@NotBlank String email,
                           @NotBlank @Size(max = 72) String password) {
    @Override public String toString() { return "LoginRequest[redacted]"; }
}
