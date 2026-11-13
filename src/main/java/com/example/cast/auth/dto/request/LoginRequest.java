package com.example.cast.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 로그인도 BCrypt 입력 한도를 적용하지만 기존 계정에 가입 최소 길이를 소급하지 않는다. */
@Schema(description = "이메일 로그인 요청")
public record LoginRequest(
                           @Schema(description = "가입한 이메일. 앞뒤 공백 제거 및 소문자 정규화", example = "user@example.com", format = "email")
                           @NotBlank String email,
                           @Schema(description = "기존 비밀번호. UTF-8 최대 72바이트", example = "MySecurePassword123!",
                                   format = "password", accessMode = Schema.AccessMode.WRITE_ONLY)
                           @NotBlank @Size(max = 72) String password) {
    @Override public String toString() { return "LoginRequest[redacted]"; }
}
