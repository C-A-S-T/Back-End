package com.example.cast.source.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Schema(description = "리서치 근거 원문 스크랩 요청")
public record SentenceScrapCreateRequest(
        @NotNull @Positive @Schema(description = "근거 원문 ID", example = "45") Long evidenceId
) {}
