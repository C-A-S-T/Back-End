
package com.example.cast.ai;

import com.example.cast.ai.client.AiClient;
import com.example.cast.ai.dto.AiGenerateResponse;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.*;

class AiClientTest {

    @Test
    void fastApiConnectionTest() {

        RestClient.Builder builder = RestClient.builder();

        AiClient aiClient = new AiClient(
                builder,
                "http://127.0.0.1:8000"
        );

        AiGenerateResponse response = aiClient.generate(
                1L,
                "2026년 청년월세 지원 조건이 무엇인가요?"
        );

        assertNotNull(response);
        assertEquals(1L, response.taskId());
        assertEquals("received", response.status());

        System.out.println("FastAPI 응답: " + response);
    }
}
