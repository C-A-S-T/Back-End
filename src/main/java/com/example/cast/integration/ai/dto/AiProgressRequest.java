package com.example.cast.integration.ai.dto;

import jakarta.validation.constraints.*;
import java.util.UUID;

public record AiProgressRequest(
        @NotNull UUID jobId,
        @NotNull @Min(2) @Max(5) Short currentStep,
        @Size(max = 350) String previewSummary
) {}
