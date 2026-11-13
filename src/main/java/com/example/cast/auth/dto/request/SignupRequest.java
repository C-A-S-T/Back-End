package com.example.cast.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 원문 비밀번호는 입력 처리에만 사용하며 문자열 표현에 포함하지 않는다. */
public record SignupRequest(@NotBlank String email,
                            @NotBlank @Size(min = 8, max = 72)
                            @Pattern(regexp = "(?s)(?=.*[A-Za-z])(?=.*[0-9])(?=.*\\p{Punct}).*",
                                    message = "영문, 숫자, 특수문자를 포함해 8자 이상이어야 합니다.")
                            String password) {
    @Override public String toString() { return "SignupRequest[redacted]"; }
}
