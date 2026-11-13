package com.example.cast.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 링큐의 상대 서버 URL과 JWT Bearer 문서 설정을 CAST의 인증 계약에 맞춘다. */
@Configuration(proxyBeanMethods = false)
public class SwaggerConfig {
    public static final String JWT_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI castOpenApi() {
        return new OpenAPI()
                .info(new Info().title("C.A.S.T. 인증 API").version("v1")
                        .description("이메일 회원가입 및 로그인 API. 회원가입과 로그인은 인증 없이 호출합니다. "
                                + "성공 응답의 accessToken은 60분 동안 유효합니다. "
                                + "보호 API의 Authorize에는 Bearer 접두사 없이 토큰 값만 입력합니다."))
                .addServersItem(new Server().url("/"))
                .components(new Components().addSecuritySchemes(JWT_SCHEME,
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(JWT_SCHEME));
    }
}
