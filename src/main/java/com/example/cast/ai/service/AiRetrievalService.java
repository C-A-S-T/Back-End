
package com.example.cast.ai.service;

import com.example.cast.ai.client.AiClient;
import com.example.cast.ai.dto.AiGenerateResponse;
import com.example.cast.document.entity.DocumentChunk;
import com.example.cast.retrieval.entity.RetrievalResult;
import com.example.cast.retrieval.repository.RetrievalResultRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AiRetrievalService {

    private final RetrievalResultRepository retrievalResultRepository;
    private final AiClient aiClient;

    @Transactional(readOnly = true)
    public AiGenerateResponse generateFromRetrieval(
            Long taskId,
            Long runId,
            String question
    ) {

        // 1. DB에서 검색 결과 조회
        List<RetrievalResult> results =
                retrievalResultRepository.findByRunIdWithChunk(runId);

        if (results.isEmpty()) {
            throw new IllegalStateException(
                    "검색 결과가 없습니다. runId=" + runId
            );
        }

        // 2. Python에 전달할 청크 목록 생성
        List<Map<String, Object>> retrievedChunks =
                new ArrayList<>();

        for (RetrievalResult result : results) {

            DocumentChunk chunk = result.getChunk();

            Map<String, Object> chunkData = new HashMap<>();

            chunkData.put(
                    "document_id",
                    chunk.getDocument().getDocumentId()
            );

            chunkData.put(
                    "chunk_id",
                    chunk.getChunkId()
            );

            if (chunk.getPageStart() != null) {
                chunkData.put(
                        "page_number",
                        chunk.getPageStart()
                );
            }

            chunkData.put(
                    "content",
                    chunk.getChunkContent()
            );

            retrievedChunks.add(chunkData);
        }

        // 3. Python AI 서버 호출
        return aiClient.generate(
                taskId,
                question,
                retrievedChunks
        );
    }
}
