package com.example.cast.source.dto.response;

import com.example.cast.document.entity.ReliabilityLevel;
import com.example.cast.document.entity.SourceType;
import com.example.cast.source.entity.SaveMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "출처보관함 저장 문서 목록과 페이지 정보")
public record SourceDocumentListResponse(
        @Schema(description = "현재 페이지에 포함된 저장 문서") List<DocumentItem> items,
        @Schema(description = "0부터 시작하는 현재 페이지 번호", example = "0") int page,
        @Schema(description = "페이지당 문서 수", example = "20") int size,
        @Schema(description = "조건에 맞는 전체 저장 문서 수", example = "1") long totalElements,
        @Schema(description = "전체 페이지 수", example = "1") int totalPages,
        @Schema(description = "다음 페이지 존재 여부", example = "false") boolean hasNext
) {
    @Schema(description = "사용자가 저장한 출처 문서")
    public record DocumentItem(
            @Schema(description = "사용자의 저장 기록 ID", example = "12") Long savedSourceId,
            @Schema(description = "문서 스냅샷 ID", example = "31") Long documentId,
            @Schema(description = "문서 제목", example = "2026년 청년 주거 지원사업 안내") String documentTitle,
            @Schema(description = "발행 기관/출처 이름", nullable = true, example = "공공기관") String sourceName,
            @Schema(description = "문서 원문 URL", nullable = true, example = "https://example.go.kr/document") String sourceUrl,
            @Schema(description = "문서 발행일", nullable = true, example = "2026-10-15") LocalDate publishedAt,
            @Schema(description = "문서 종류", example = "GOVERNMENT") SourceType sourceType,
            @Schema(description = "문서 신뢰도", nullable = true, example = "HIGH") ReliabilityLevel reliabilityLevel,
            @Schema(description = "최초 저장 방식", example = "AUTO") SaveMethod saveMethod,
            @Schema(description = "배정된 폴더 ID. null이면 미분류", nullable = true) Long folderId,
            @Schema(description = "즐겨찾기 여부", example = "false") boolean favorite,
            @Schema(description = "최초 저장 시각(UTC)", example = "2026-11-14T08:30:00Z") Instant savedAt
    ) { }
}
