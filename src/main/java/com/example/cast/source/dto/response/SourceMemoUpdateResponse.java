package com.example.cast.source.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "저장 문서 메모 수정 결과")
public record SourceMemoUpdateResponse(
        Long savedSourceId,
        @Schema(nullable = true, maxLength = 1000) String memo
) { }
