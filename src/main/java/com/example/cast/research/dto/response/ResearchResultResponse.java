package com.example.cast.research.dto.response;

import com.example.cast.research.entity.ResearchTaskStatus;

public record ResearchResultResponse(
        Long taskId,
        Long queryId,
        ResearchTaskStatus status,
        Short currentStep,
        String previewSummary,
        String answer,
        String summary
) {
}
