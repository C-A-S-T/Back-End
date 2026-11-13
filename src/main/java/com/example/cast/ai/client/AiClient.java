
package com.example.cast.ai.client;

import com.example.cast.ai.dto.AiGenerateResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class AiClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AiClient(
            @Value("${ai.server.url:http://127.0.0.1:8000}")
            String aiServerUrl
    ) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .build();

        this.restClient = RestClient.builder()
                .baseUrl(aiServerUrl)
                .requestFactory(new JdkClientHttpRequestFactory(httpClient))
                .build();
    }

    // 여러 검색 문서 청크를 Python AI 서버로 전달
    public AiGenerateResponse generate(
            Long taskId,
            String question,
            List<Map<String, Object>> retrievedChunks
    ) {
        Map<String, Object> request = new HashMap<>();

        request.put("task_id", taskId);
        request.put("question", question);
        request.put("retrieved_chunks", retrievedChunks);

        return sendRequest(request);
    }

    // 기존 context 기반 호출 지원
    public AiGenerateResponse generate(
            Long taskId,
            String question,
            String context
    ) {
        Map<String, Object> request = new HashMap<>();

        request.put("task_id", taskId);
        request.put("question", question);
        request.put("context", context);

        return sendRequest(request);
    }

    // 문서 및 페이지 출처 정보가 포함된 호출 지원
    public AiGenerateResponse generate(
            Long taskId,
            String question,
            String context,
            Long documentId,
            Long chunkId,
            Integer pageNumber
    ) {
        Map<String, Object> request = new HashMap<>();

        request.put("task_id", taskId);
        request.put("question", question);
        request.put("context", context);

        if (documentId != null) {
            request.put("document_id", documentId);
        }

        if (chunkId != null) {
            request.put("chunk_id", chunkId);
        }

        if (pageNumber != null) {
            request.put("page_number", pageNumber);
        }

        return sendRequest(request);
    }

    // FastAPI /generate 호출
    private AiGenerateResponse sendRequest(
            Map<String, Object> request
    ) {
        String json = objectMapper.writeValueAsString(request);

        return restClient.post()
                .uri("/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .body(json)
                .retrieve()
                .body(AiGenerateResponse.class);
    }
}
