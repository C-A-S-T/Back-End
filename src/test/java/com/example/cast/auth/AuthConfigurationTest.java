package com.example.cast.auth;

import com.example.cast.global.config.JwtConfig;
import com.example.cast.global.config.SecurityConfig;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Base64;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import static org.assertj.core.api.Assertions.*;

/** DB 없이 필수 키 누락/오류에 따른 시작 거부를 검증한다. 실제 운영 키를 읽지 않는다. */
class AuthConfigurationTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(JwtConfig.class).withBean(Clock.class, Clock::systemUTC);

    @Test void missingKeyPreventsStartup() {
        context.run(application -> assertThat(application).hasFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "not-base64!", "c2hvcnQ=", " "})
    void invalidOrShortKeyPreventsStartupWithoutPrintingIt(String key) {
        context.withPropertyValues("cast.jwt.secret-base64=" + key).run(application -> {
            assertThat(application).hasFailed();
            assertThat(application.getStartupFailure()).hasRootCauseMessage(
                    "CAST_JWT_SECRET_BASE64 must be valid Base64 of at least 32 bytes");
        });
    }

    @Test void validTestOnlyKeyCreatesHs256ComponentsAndStrength10Encoder() {
        String key = Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));
        context.withPropertyValues("cast.jwt.secret-base64=" + key).run(application -> {
            assertThat(application).hasNotFailed().hasSingleBean(JwtEncoder.class).hasSingleBean(JwtDecoder.class);
            assertThat(application.getBean(SecretKey.class).getEncoded()).hasSize(32);
            var encoder = application.getBean(PasswordEncoder.class);
            String hash = encoder.encode("Password123!");
            assertThat(hash).startsWith("$2a$10$");
            assertThat(encoder.matches("Password123!", hash)).isTrue();
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"*", "https://*.example.com", "", "https://example.com/", "https://example.com/path",
            "https://example.com?query=1", "https://user@example.com", "file://localhost", "https://example.com,"})
    void corsRejectsWildcardsAndNonOrigins(String origin) {
        assertThatThrownBy(() -> new SecurityConfig().corsConfigurationSource(origin)).isInstanceOf(IllegalStateException.class);
    }
}
