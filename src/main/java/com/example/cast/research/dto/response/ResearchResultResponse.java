package com.example.cast.research.dto.response;

import com.example.cast.research.entity.ResearchTaskStatus;
import io.swagger.v3.oas.annotations.media.Schema;

public record ResearchResultResponse(
        Long taskId,
        Long queryId,
        ResearchTaskStatus status,
        Short currentStep,
        String previewSummary,
        String answer,
        String summary,
        @Schema(description = "Original research question") String question,
        @Schema(description = "Research title") String researchTitle
) {
}
