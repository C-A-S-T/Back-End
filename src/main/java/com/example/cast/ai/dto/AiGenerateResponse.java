
package com.example.cast.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record AiGenerateResponse(
        @JsonProperty("task_id")
        Long taskId,

        String status,
        String question,
        String answer,
        String summary,

        List<ClaimResult> claims
) {

    public record ClaimResult(
            String content,

            @JsonProperty("claim_type")
            String claimType,

            @JsonProperty("verification_status")
            String verificationStatus,

            List<EvidenceResult> evidence
    ) {
    }

    public record EvidenceResult(
            @JsonProperty("document_id")
            Long documentId,

            @JsonProperty("chunk_id")
            Long chunkId,

            @JsonProperty("page_number")
            Integer pageNumber,

            String relation,
            String excerpt
    ) {
    }
}
