package com.example.cast.research.dto.response;

import com.example.cast.research.entity.ResearchTaskStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

@Schema(description = "Research history and pagination")
public record ResearchListResponse(
        @Schema(description = "Research tasks on this page")
        List<ResearchItem> items,
        @Schema(description = "Zero-based page number", example = "0")
        int page,
        @Schema(description = "Page size", example = "20")
        int size,
        @Schema(description = "Total tasks owned by the user")
        long totalElements,
        @Schema(description = "Total pages")
        int totalPages,
        @Schema(description = "Whether another page exists")
        boolean hasNext
) {
    public record ResearchItem(
            Long taskId,
            String question,
            ResearchTaskStatus status,
            Instant createdAt,
            @Schema(description = "Research title")
            String researchTitle
    ) {}
}
