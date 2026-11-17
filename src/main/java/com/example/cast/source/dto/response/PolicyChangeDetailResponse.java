package com.example.cast.source.dto.response;

import com.example.cast.document.entity.ReliabilityLevel;
import com.example.cast.document.entity.SourceType;
import com.example.cast.source.entity.SourceFolderCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;

@Schema(description = "정책 변경 요약과 이전·이후 문서 메타데이터 및 로그인 사용자 영향")
public record PolicyChangeDetailResponse(
        @Schema(description = "정책 변경 기록 ID") Long policyChangeId,
        @Schema(description = "정책명") String policyTitle,
        @Schema(description = "정책 분야") SourceFolderCategory category,
        @Schema(description = "저장된 주요 변경 요약. 조항별 AI 비교 결과가 아님") String changeSummary,
        @Schema(description = "정책 변경 확인 시각(UTC)") Instant confirmedAt,
        @Schema(description = "이전 비교 대상 문서") DocumentInfo previousDocument,
        @Schema(description = "이후 비교 대상 문서. 최신 버전임을 보장하지 않음") DocumentInfo currentDocument,
        @Schema(description = "현재 사용자 재확인 필요 여부. null은 미평가", nullable = true) Boolean requiresRecheck,
        @Schema(description = "현재 사용자에게 미치는 영향", nullable = true) String impactSummary,
        @Schema(description = "사용자 영향 평가 시각(UTC)", nullable = true) Instant evaluatedAt
) {
    @Schema(description = "정책 문서 스냅샷의 출처 메타데이터")
    public record DocumentInfo(
            @Schema(description = "백엔드 문서 ID") Long documentId,
            @Schema(description = "문서 제목") String documentTitle,
            @Schema(description = "문서 버전", nullable = true) String documentVersion,
            @Schema(description = "발행 기관", nullable = true) String sourceName,
            @Schema(description = "원문 URL", nullable = true) String sourceUrl,
            @Schema(description = "발행일", nullable = true) LocalDate publishedAt,
            @Schema(description = "시행 시작일", nullable = true) LocalDate effectiveFrom,
            @Schema(description = "시행 종료일", nullable = true) LocalDate effectiveTo,
            @Schema(description = "출처 유형") SourceType sourceType,
            @Schema(description = "신뢰도 등급", nullable = true) ReliabilityLevel reliabilityLevel
    ) { }
}
