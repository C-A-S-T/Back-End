package com.example.cast.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** 가입 직후 자동 로그인과 일반 로그인이 공유하는 응답. 토큰은 로그에 남기지 않는다. */
@Schema(description = "회원가입 직후 자동 로그인과 일반 로그인이 공유하는 인증 결과")
public record AuthResponse(
        @Schema(description = "사용자 ID", example = "1") Long userId,
        @Schema(description = "저장된 이메일", example = "user@example.com") String email,
        @Schema(description = "HS256 JWT. 보호 API의 Authorization: Bearer 헤더로 전달", example = "<발급된 JWT>") String accessToken,
        @Schema(description = "인증 방식", example = "Bearer") String tokenType,
        @Schema(description = "토큰 유효 기간(초)", example = "3600") long expiresIn) {
    @Override public String toString() { return "AuthResponse[userId=" + userId + ", credentials=redacted]"; }
}
