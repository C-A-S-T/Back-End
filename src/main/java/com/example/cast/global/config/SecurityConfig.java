package com.example.cast.global.config;

import com.example.cast.auth.code.AuthErrorCode;
import com.example.cast.auth.security.UserJwtAuthenticationConverter;
import com.example.cast.global.code.ErrorCode;
import com.example.cast.global.code.GlobalErrorCode;
import com.example.cast.global.response.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.ObjectMapper;

/** 일반 API는 JWT 무상태 인증을 사용한다. 우선순위 1의 AI 전용 체인은 별도로 유지한다. */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SecurityConfig {
    @Bean
    @Order(2)
    public SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper mapper,
            UserJwtAuthenticationConverter converter, UrlBasedCorsConfigurationSource corsConfigurationSource) throws Exception {
        AuthenticationEntryPoint entryPoint = (request, response, exception) -> {
            boolean disabled = exception instanceof OAuth2AuthenticationException oauth
                    && UserJwtAuthenticationConverter.ACCOUNT_DISABLED.equals(oauth.getError().getErrorCode());
            if (!disabled) response.setHeader("WWW-Authenticate", "Bearer");
            write(response, mapper, disabled ? AuthErrorCode.ACCOUNT_DISABLED : GlobalErrorCode.UNAUTHORIZED);
        };
        http.sessionManagement(config -> config.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)
            .requestCache(AbstractHttpConfigurer::disable)
            // 쿠키/세션을 인증 수단으로 사용하지 않으며 Authorization 헤더만 허용한다.
            .csrf(AbstractHttpConfigurer::disable)
            .cors(config -> config.configurationSource(corsConfigurationSource))
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers(HttpMethod.POST, "/api/v1/auth/signup", "/api/v1/auth/login").permitAll()
                    .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                    .anyRequest().authenticated())
            .oauth2ResourceServer(config -> config
                    .jwt(jwt -> jwt.jwtAuthenticationConverter(converter))
                    .authenticationEntryPoint(entryPoint)
                    .accessDeniedHandler((request, response, exception) -> write(response, mapper, GlobalErrorCode.FORBIDDEN)))
            .exceptionHandling(config -> config.authenticationEntryPoint(entryPoint)
                    .accessDeniedHandler((request, response, exception) -> write(response, mapper, GlobalErrorCode.FORBIDDEN)));
        return http.build();
    }

    /** 쉼표로 구분한 명시적 Origin만 허용한다. 와일드카드와 경로가 포함된 URL은 거부한다. */
    @Bean
    public UrlBasedCorsConfigurationSource corsConfigurationSource(
            @Value("${cast.cors.allowed-origins:http://localhost:5173}") String configuredOrigins) {
        var origins = Arrays.stream(configuredOrigins.split(",", -1)).map(String::trim).toList();
        for (String origin : origins) {
            URI uri;
            try { uri = URI.create(origin); }
            catch (IllegalArgumentException exception) { throw new IllegalStateException("Invalid CAST_CORS_ALLOWED_ORIGINS"); }
            if (origin.contains("*") || uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getQuery() != null || uri.getFragment() != null
                    || (uri.getRawPath() != null && !uri.getRawPath().isEmpty())
                    || !("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))) {
                throw new IllegalStateException("CAST_CORS_ALLOWED_ORIGINS must contain explicit HTTP(S) origins");
            }
        }
        var cors = new CorsConfiguration();
        cors.setAllowedOrigins(origins);
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        cors.setAllowCredentials(false);
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return source;
    }

    private static void write(HttpServletResponse response, ObjectMapper mapper, ErrorCode code) throws IOException {
        response.setStatus(code.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(mapper.writeValueAsString(ApiResponse.errorBody(code)));
    }
}
