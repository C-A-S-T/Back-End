package com.example.cast.global.config;
import com.example.cast.global.code.GlobalErrorCode;
import com.example.cast.global.response.ApiResponse;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;
/** 기본 폼 로그인/Basic/CSRF와 기존 health 공개 정책을 유지한다. */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SecurityConfig {
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper mapper) throws Exception {
        MediaTypeRequestMatcher html = new MediaTypeRequestMatcher(MediaType.TEXT_HTML);
        html.setIgnoredMediaTypes(Set.of(MediaType.ALL));
        var login = new LoginUrlAuthenticationEntryPoint("/login");
        AuthenticationEntryPoint basic = (request, response, exception) -> {
            response.setHeader("WWW-Authenticate", "Basic realm=\"Realm\"");
            write(response, mapper, GlobalErrorCode.UNAUTHORIZED);
        };
        AuthenticationEntryPoint negotiated = (request, response, exception) -> {
            if (html.matches(request)) login.commence(request, response, exception);
            else basic.commence(request, response, exception);
        };
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers(EndpointRequest.to("health")).permitAll()
                .anyRequest().authenticated())
            .formLogin(form -> form.failureHandler((request, response, exception) ->
                    write(response, mapper, GlobalErrorCode.UNAUTHORIZED)))
            .httpBasic(config -> config.authenticationEntryPoint(basic))
            .exceptionHandling(config -> config.authenticationEntryPoint(negotiated)
                    .accessDeniedHandler((request, response, exception) ->
                            write(response, mapper, GlobalErrorCode.FORBIDDEN)));
        return http.build();
    }
    private static void write(HttpServletResponse response, ObjectMapper mapper,
            GlobalErrorCode code) throws IOException {
        response.setStatus(code.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(mapper.writeValueAsString(ApiResponse.errorBody(code)));
    }
}
