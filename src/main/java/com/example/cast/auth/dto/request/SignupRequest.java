package com.example.cast.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 원문 비밀번호는 입력 처리에만 사용하며 문자열 표현에 포함하지 않는다. */
public record SignupRequest(@NotBlank String email,
                            @NotBlank @Size(min = 8, max = 72) String password) {
    @Override public String toString() { return "SignupRequest[redacted]"; }
}
