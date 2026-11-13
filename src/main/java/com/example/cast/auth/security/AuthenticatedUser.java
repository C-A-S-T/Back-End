package com.example.cast.auth.security;

import java.security.Principal;

/** 검증된 JWT와 현재 DB 계정으로부터 구성한 사용자. 소유권 검사는 이 ID를 기준으로 한다. */
public record AuthenticatedUser(Long userId) implements Principal {
    @Override public String getName() { return userId.toString(); }
}
