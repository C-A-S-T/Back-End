
package com.example.cast.ai;

import com.example.cast.ai.dto.AiGenerateResponse;
import com.example.cast.ai.service.AiResultSaveService;
import com.example.cast.ai.service.AiRetrievalService;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import com.example.cast.CastApplication;

public class AiDbIntegrationCheck {

    public static void main(String[] args) {

        // 테스트용 Spring Boot 실행
        SpringApplication application =
                new SpringApplication(CastApplication.class);

        // 웹 서버를 중복 실행하지 않음
        application.setWebApplicationType(
                org.springframework.boot.WebApplicationType.NONE
        );

        try (ConfigurableApplicationContext context =
                     application.run(args)) {

            AiRetrievalService retrievalService =
                    context.getBean(AiRetrievalService.class);

            AiResultSaveService saveService =
                    context.getBean(AiResultSaveService.class);

            Long taskId = 1L;
            Long runId = 1L;

            String question =
                    "2026년 청년월세지원 사업의 지원 대상과 지원 금액은 무엇인가요?";

            System.out.println("================================");
            System.out.println("1. PostgreSQL 검색 결과 조회");
            System.out.println("================================");

            // DB에서 검색 결과를 가져와 Python AI 서버로 전달
            AiGenerateResponse response =
                    retrievalService.generateFromRetrieval(
                            taskId,
                            runId,
                            question
                    );

            System.out.println("AI 답변: " + response.answer());
            System.out.println("AI 요약: " + response.summary());

            System.out.println("================================");
            System.out.println("2. AI 결과 PostgreSQL 저장");
            System.out.println("================================");

            // 답변, Claim, Verification, Evidence 저장
            Long answerId = saveService.save(
                    taskId,
                    runId,
                    response
            );

            System.out.println("저장된 Answer ID: " + answerId);

            System.out.println("================================");
            System.out.println("통합 테스트 성공!");
            System.out.println("================================");
        }
    }
}
