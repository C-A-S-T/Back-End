package com.example.cast.integration.ai.dto;

import com.example.cast.research.entity.ResearchTaskStatus;

public record AiProgressReceipt(
        Long taskId,
        ResearchTaskStatus status,
        Short currentStep,
        String previewSummary
) {}
