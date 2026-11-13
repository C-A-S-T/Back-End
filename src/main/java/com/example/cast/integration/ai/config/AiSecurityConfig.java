package com.example.cast.integration.ai.config;
import com.example.cast.global.code.GlobalErrorCode;
import com.example.cast.global.response.ApiResponse;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;
/** 사용자 로그인과 분리된 서버 간 인증. CSRF 예외는 내부 AI 경로에만 적용한다. */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class AiSecurityConfig {
    @Bean @Order(1)
    public SecurityFilterChain aiSecurityFilterChain(HttpSecurity http, AiProperties properties,
            ObjectMapper mapper) throws Exception {
        var filter = new OncePerRequestFilter() {
            @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                    FilterChain chain) throws ServletException, IOException {
                String expected = properties.callbackToken();
                String actual = request.getHeader("X-AI-Service-Token");
                if (expected.isBlank() || actual == null || !MessageDigest.isEqual(
                        expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8))) {
                    response.setStatus(401); response.setContentType("application/json"); response.setCharacterEncoding("UTF-8");
                    response.getWriter().write(mapper.writeValueAsString(ApiResponse.errorBody(GlobalErrorCode.UNAUTHORIZED)));
                    return;
                }
                var context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(new UsernamePasswordAuthenticationToken("ai-server", null,
                        List.of(new SimpleGrantedAuthority("ROLE_AI_SERVER"))));
                SecurityContextHolder.setContext(context);
                try { chain.doFilter(request, response); } finally { SecurityContextHolder.clearContext(); }
            }
        };
        return http.securityMatcher("/internal/ai/**")
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(c -> c.disable())
                .authorizeHttpRequests(a -> a.anyRequest().hasRole("AI_SERVER"))
                .addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class).build();
    }
}
