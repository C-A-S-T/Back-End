package com.example.cast.integration.ai.config;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
/** AI 통신 설정과 전송 대기 작업 스케줄러. */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(AiProperties.class)
public class AiIntegrationConfig {}
