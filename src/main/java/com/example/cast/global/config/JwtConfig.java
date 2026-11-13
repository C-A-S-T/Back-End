package com.example.cast.global.config;

import com.example.cast.auth.service.JwtTokenService;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;

/** 실제 키에 기본값을 제공하지 않으며 잘못된 설정은 시작 시 즉시 거부한다. */
@Configuration(proxyBeanMethods = false)
public class JwtConfig {
    @Bean
    public PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(10); }

    @Bean
    public SecretKey jwtSecretKey(@Value("${cast.jwt.secret-base64:}") String encoded) {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException exception) {
            // 예외의 입력값/원인을 전파하지 않아 서명 키가 시작 로그에 노출되지 않게 한다.
            throw new IllegalStateException("CAST_JWT_SECRET_BASE64 must be valid Base64 of at least 32 bytes");
        }
        if (bytes.length < 32) {
            throw new IllegalStateException("CAST_JWT_SECRET_BASE64 must be valid Base64 of at least 32 bytes");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    public JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecretKey));
    }

    @Bean
    public JwtDecoder jwtDecoder(SecretKey jwtSecretKey, Clock clock) {
        var decoder = NimbusJwtDecoder.withSecretKey(jwtSecretKey).macAlgorithm(MacAlgorithm.HS256).build();
        var timestamps = new JwtTimestampValidator(Duration.ZERO);
        timestamps.setClock(clock);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(timestamps,
                new JwtIssuerValidator(JwtTokenService.ISSUER), requiredClaims(clock)));
        return decoder;
    }

    /** 누락된 exp/iat, 미래 발급, 양수가 아닌 ID도 서명만 맞다는 이유로 수락하지 않는다. */
    private OAuth2TokenValidator<Jwt> requiredClaims(Clock clock) {
        return jwt -> {
            try {
                String subject = jwt.getSubject();
                boolean valid = subject != null && subject.matches("[1-9][0-9]*")
                        && Long.parseLong(subject) > 0
                        && jwt.getIssuedAt() != null && jwt.getExpiresAt() != null
                        && !jwt.getIssuedAt().isAfter(clock.instant())
                        && jwt.getExpiresAt().isAfter(clock.instant())
                        && jwt.getExpiresAt().isAfter(jwt.getIssuedAt());
                if (valid) return OAuth2TokenValidatorResult.success();
            } catch (IllegalArgumentException ignored) {
                // 외부 토큰의 값은 응답이나 로그에 되돌려 주지 않는다.
            }
            return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Invalid token claims", null));
        };
    }
}
