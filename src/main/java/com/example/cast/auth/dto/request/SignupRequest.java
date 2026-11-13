package com.example.cast.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 원문 비밀번호는 입력 처리에만 사용하며 문자열 표현에 포함하지 않는다. */
@Schema(description = "이메일 회원가입 요청")
public record SignupRequest(
                            @Schema(description = "앞뒤 공백 제거 및 소문자 정규화 후 검증하는 이메일", example = "user@example.com", format = "email")
                            @NotBlank String email,
                            @Schema(description = "영문·숫자·ASCII 특수문자를 각각 포함해 8자 이상. UTF-8 최대 72바이트",
                                    example = "MySecurePassword123!", format = "password", accessMode = Schema.AccessMode.WRITE_ONLY)
                            @NotBlank @Size(min = 8, max = 72)
                            // Java와 OpenAPI/JavaScript가 동일하게 해석하는 ASCII 특수문자 범위.
                            @Pattern(regexp = "(?=[\\s\\S]*[A-Za-z])(?=[\\s\\S]*[0-9])(?=[\\s\\S]*[\\x21-\\x2F\\x3A-\\x40\\x5B-\\x60\\x7B-\\x7E])[\\s\\S]*",
                                    message = "영문, 숫자, 특수문자를 포함해 8자 이상이어야 합니다.")
                            String password) {
    @Override public String toString() { return "SignupRequest[redacted]"; }
}
