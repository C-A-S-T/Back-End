package com.example.cast.integration.ai.client;

import com.example.cast.global.code.GlobalErrorCode;
import com.example.cast.global.exception.BusinessException;
import com.example.cast.integration.ai.config.AiProperties;
import com.example.cast.policycompare.code.PolicyComparisonErrorCode;
import com.example.cast.policycompare.dto.response.PolicyComparisonResponse;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;

/** 별도 인증 토큰으로 FastAPI의 읽기 전용 정책 비교 결과를 조회한다. */
@Component
@RequiredArgsConstructor
public class PolicyComparisonClient {
    private static final int MAX_RESPONSE_BYTES = 8 * 1024 * 1024;
    private final AiProperties properties;
    private final ObjectMapper mapper;
    private final HttpClient client = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    public PolicyComparisonResponse fetchComparison(String policyKey) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("AI HTTP calls must run outside database transactions");
        }
        if (!properties.enabled() || properties.baseUrl() == null
                || properties.apiToken() == null || properties.apiToken().isBlank()) {
            throw new BusinessException(PolicyComparisonErrorCode.AI_UNAVAILABLE);
        }

        String path = "/v1/policy-comparisons/" + policyKey;
        try {
            HttpResponse<InputStream> response = client.send(
                    newRequest(path), HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() == 404) {
                // FastAPI의 새 경로가 배포되기 전까지 기존 경로를 임시 지원한다.
                try (InputStream unused = response.body()) {
                    // Close the first response before requesting the legacy route.
                }
                response = client.send(newRequest(path + "/preview"),
                        HttpResponse.BodyHandlers.ofInputStream());
            }
            try (InputStream body = response.body()) {
                int status = response.statusCode();
                if (status == 404) {
                    throw new BusinessException(GlobalErrorCode.NOT_FOUND);
                }
                if (status == 503) {
                    throw new BusinessException(PolicyComparisonErrorCode.AI_UNAVAILABLE);
                }
                if (status != 200) {
                    throw new BusinessException(PolicyComparisonErrorCode.AI_BAD_GATEWAY);
                }

                byte[] bytes = body.readNBytes(MAX_RESPONSE_BYTES + 1);
                if (bytes.length == 0 || bytes.length > MAX_RESPONSE_BYTES) {
                    throw new BusinessException(PolicyComparisonErrorCode.AI_BAD_GATEWAY);
                }
                try {
                    return mapper.readValue(bytes, PolicyComparisonResponse.class);
                } catch (Exception e) {
                    // Never expose the upstream body, the source document, or token.
                    throw new BusinessException(PolicyComparisonErrorCode.AI_BAD_GATEWAY);
                }
            }
        } catch (HttpTimeoutException e) {
            throw new BusinessException(PolicyComparisonErrorCode.AI_TIMEOUT);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(PolicyComparisonErrorCode.AI_UNAVAILABLE);
        } catch (IOException e) {
            throw new BusinessException(PolicyComparisonErrorCode.AI_UNAVAILABLE);
        }
    }

    private HttpRequest newRequest(String path) {
        return HttpRequest.newBuilder(properties.baseUrl().resolve(path))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json")
                .header("X-AI-Service-Token", properties.apiToken())
                .GET()
                .build();
    }
}
