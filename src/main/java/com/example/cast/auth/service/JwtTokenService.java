package com.example.cast.auth.service;

import com.example.cast.auth.dto.response.AuthResponse;
import com.example.cast.user.entity.User;
import java.time.Clock;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

/** 개인 정보 없이 사용자 ID와 발급/만료 시각만 포함하는 Access Token 발급기. */
@Service
@RequiredArgsConstructor
public class JwtTokenService {
    public static final String ISSUER = "cast-api";
    public static final Duration ACCESS_TOKEN_TTL = Duration.ofMinutes(60);
    private final JwtEncoder encoder;
    private final Clock clock;

    public AuthResponse issue(User user) {
        var now = clock.instant();
        var claims = JwtClaimsSet.builder().issuer(ISSUER).subject(user.getUserId().toString())
                .issuedAt(now).expiresAt(now.plus(ACCESS_TOKEN_TTL)).build();
        var header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new AuthResponse(user.getUserId(), user.getEmail(), token, "Bearer", ACCESS_TOKEN_TTL.toSeconds());
    }
}
