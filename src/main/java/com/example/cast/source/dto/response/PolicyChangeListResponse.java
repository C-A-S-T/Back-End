package com.example.cast.source.dto.response;

import com.example.cast.source.entity.SourceFolderCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

@Schema(description = "Confirmed policy-change cards and page metadata")
public record PolicyChangeListResponse(
        @Schema(description = "정책 변경 카드 목록") List<PolicyChangeItem> items,
        @Schema(description = "현재 페이지 (0부터 시작)", example = "0") int page,
        @Schema(description = "페이지당 항목 수", example = "20") int size,
        @Schema(description = "필터 조건에 맞는 전체 변경 기록 수") long totalElements,
        @Schema(description = "전체 페이지 수") int totalPages,
        @Schema(description = "다음 페이지 존재 여부") boolean hasNext
) {
    @Schema(description = "확인된 정책 변경 이력 및 로그인 사용자에게 평가된 영향")
    public record PolicyChangeItem(
            @Schema(description = "변경 기록 ID") Long policyChangeId,
            @Schema(description = "정책명") String policyTitle,
            @Schema(description = "정책 분야") SourceFolderCategory category,
            @Schema(description = "변경 이전 백엔드 문서 ID") Long previousDocumentId,
            @Schema(description = "변경 이전 문서 버전", nullable = true) String previousDocumentVersion,
            @Schema(description = "비교 이후 백엔드 문서 ID (전 세계 최신 버전 보장은 아님)") Long currentDocumentId,
            @Schema(description = "비교 이후 문서 버전", nullable = true) String currentDocumentVersion,
            @Schema(description = "확인된 주요 변경 내용") String changeSummary,
            @Schema(description = "로그인 사용자의 재확인 필요 여부. null은 평가되지 않음을 의미", nullable = true)
            Boolean requiresRecheck,
            @Schema(description = "로그인 사용자에게 미치는 영향 설명", nullable = true) String impactSummary,
            @Schema(description = "정책 변경 비교가 확인된 시각(UTC)") Instant confirmedAt
    ) { }
}
