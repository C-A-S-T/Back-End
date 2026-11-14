package com.example.cast.source.dto.response;

import com.example.cast.document.entity.ReliabilityLevel;
import com.example.cast.document.entity.SourceType;
import com.example.cast.source.entity.SaveMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;

@Schema(description = "사용자가 저장한 출처 문서의 메타데이터 상세 정보")
public record SourceDocumentDetailResponse(
        @Schema(description = "사용자의 저장 기록 ID", example = "12") Long savedSourceId,
        @Schema(description = "백엔드 문서 스냅샷 ID", example = "31") Long documentId,
        @Schema(description = "문서 제목", example = "가상 A 청년월세지원 사업 공고문") String documentTitle,
        @Schema(description = "발행 기관/출처", nullable = true, example = "가상 청년지원센터") String sourceName,
        @Schema(description = "원문 URL", nullable = true, example = "https://example.go.kr/document") String sourceUrl,
        @Schema(description = "발행일", nullable = true, example = "2026-10-01") LocalDate publishedAt,
        @Schema(description = "정책 시행 시작일", nullable = true, example = "2026-11-01") LocalDate effectiveFrom,
        @Schema(description = "정책 시행 종료일", nullable = true) LocalDate effectiveTo,
        @Schema(description = "문서에 저장된 버전 문자열. 최신 버전임을 보장하지 않음", nullable = true, example = "2026-1") String documentVersion,
        @Schema(description = "출처 유형", example = "GOVERNMENT") SourceType sourceType,
        @Schema(description = "신뢰도 등급. 최신성·정책 유효성 판정은 별도", nullable = true, example = "HIGH") ReliabilityLevel reliabilityLevel,
        @Schema(description = "최초 저장 방식", example = "AUTO") SaveMethod saveMethod,
        @Schema(description = "소속 폴더 ID. null이면 미분류", nullable = true) Long folderId,
        @Schema(description = "즐겨찾기 여부", example = "false") boolean favorite,
        @Schema(description = "최초 자동 저장을 유발한 연구 작업 ID. 없으면 null", nullable = true, example = "7") Long originTaskId,
        @Schema(description = "최초 저장 시각(UTC)", example = "2026-11-14T08:30:00Z") Instant savedAt
) { }
