package com.example.cast.integration.ai.client;
import com.example.cast.integration.ai.config.AiProperties;
import com.example.cast.integration.ai.dto.AiJobRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;
import java.net.http.*;
import java.time.Duration;
import java.io.IOException;
/** AI 요청은 DB 트랜잭션 밖에서 보내며 HTTP redirect는 따라가지 않는다. */
@Component
@RequiredArgsConstructor
public class AiServerClient {
    private final AiProperties properties;
    private final ObjectMapper mapper;
    private final HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(3)).build();
    public void submit(AiJobRequest job) throws IOException, InterruptedException {
        if (TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("AI HTTP calls must run outside database transactions");
        if (properties.baseUrl() == null || properties.apiToken().isBlank())
            throw new IllegalStateException("AI endpoint is not configured");
        var json = mapper.writeValueAsBytes(job);
        var request = HttpRequest.newBuilder(properties.baseUrl().resolve("/v1/research-jobs"))
                .timeout(Duration.ofSeconds(30)).header("Content-Type", "application/json")
                .header("X-AI-Service-Token", properties.apiToken())
                .header("Idempotency-Key", job.jobId().toString())
                .POST(HttpRequest.BodyPublishers.ofByteArray(json)).build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300)
            throw new IOException("AI job submission returned HTTP " + response.statusCode() + ", body=" + response.body());
    }
}
