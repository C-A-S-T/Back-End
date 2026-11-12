package com.example.cast.auth.dto.response;

/** 가입 직후 자동 로그인과 일반 로그인이 공유하는 응답. 토큰은 로그에 남기지 않는다. */
public record AuthResponse(Long userId, String email, String accessToken,
                           String tokenType, long expiresIn) {
    @Override public String toString() { return "AuthResponse[userId=" + userId + ", credentials=redacted]"; }
}
