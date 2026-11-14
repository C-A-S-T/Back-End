package com.example.cast.source.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "스크랩 생성/중복 저장 결과")
public record SentenceScrapSaveResponse(
        @Schema(description = "사용자의 스크랩 ID") Long scrapId,
        @Schema(description = "스크랩한 근거 ID") Long evidenceId,
        @Schema(description = "최초 스크랩 시각(UTC)") Instant scrappedAt,
        @Schema(description = "이번 요청으로 새로 생성했는지 여부") boolean created
) {}
