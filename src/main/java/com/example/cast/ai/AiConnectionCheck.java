
package com.example.cast.ai;

import com.example.cast.ai.client.AiClient;
import com.example.cast.ai.dto.AiGenerateResponse;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AiConnectionCheck {

    public static void main(String[] args) {

        // Python FastAPI 서버 주소
        AiClient aiClient = new AiClient("http://127.0.0.1:8000");

        // 첫 번째 테스트 문서 청크
        Map<String, Object> chunk1 = new HashMap<>();
        chunk1.put("document_id", 1L);
        chunk1.put("chunk_id", 1L);
        chunk1.put("page_number", 1);
        chunk1.put(
                "content",
                "2026년 청년월세지원 사업의 지원 대상은 19세부터 39세까지의 청년입니다."
        );

        // 두 번째 테스트 문서 청크
        Map<String, Object> chunk2 = new HashMap<>();
        chunk2.put("document_id", 1L);
        chunk2.put("chunk_id", 2L);
        chunk2.put("page_number", 3);
        chunk2.put(
                "content",
                "2026년 청년월세지원 사업의 지원 금액은 월 20만 원입니다."
        );

        List<Map<String, Object>> retrievedChunks =
                List.of(chunk1, chunk2);

        // Python AI 서버에 답변 생성 요청
        AiGenerateResponse response = aiClient.generate(
                205L,
                "2026년 청년월세지원 사업의 지원 대상과 지원 금액은 무엇인가요?",
                retrievedChunks
        );

        // 응답 결과 출력
        System.out.println("================================");
        System.out.println("Python AI 서버 연결 테스트");
        System.out.println("================================");

        System.out.println("Task ID: " + response.taskId());
        System.out.println("Answer: " + response.answer());
        System.out.println("Summary: " + response.summary());

        System.out.println("================================");
        System.out.println("Claim 검증 결과");
        System.out.println("================================");

        if (response.claims() != null) {
            for (AiGenerateResponse.ClaimResult claim : response.claims()) {

                System.out.println("Claim: " + claim.content());
                System.out.println("Type: " + claim.claimType());
                System.out.println("Status: " + claim.verificationStatus());

                if (claim.evidence() != null) {
                    for (AiGenerateResponse.EvidenceResult evidence : claim.evidence()) {
                        System.out.println("  Document ID: " + evidence.documentId());
                        System.out.println("  Chunk ID: " + evidence.chunkId());
                        System.out.println("  Page: " + evidence.pageNumber());
                        System.out.println("  Relation: " + evidence.relation());
                        System.out.println("  Excerpt: " + evidence.excerpt());
                    }
                }

                System.out.println("--------------------------------");
            }
        }
    }
}
