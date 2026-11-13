package com.example.cast.source.dto.response;

import com.example.cast.source.entity.SourceFolderCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "출처보관함 개요 통계")
public record SourceOverviewResponse(
        @Schema(description = "자동·수동 저장을 포함한 전체 문서 수", example = "24")
        long totalSavedDocumentCount,
        @Schema(description = "최초 저장 방식이 AUTO인 문서 수", example = "20")
        long autoSavedDocumentCount,
        @Schema(description = "문장 스크랩 기능 구현 전까지 null 반환", nullable = true)
        Long scrappedSentenceCount,
        @Schema(description = "정책 변경 추적 기능 구현 전까지 null 반환", nullable = true)
        Long changedPolicyCount,
        @Schema(description = "사용자에게 존재하는 고정 카테고리 폴더 수", example = "3")
        int folderCount,
        @Schema(description = "폴더에 배정되지 않은 저장 문서 수", example = "2")
        long unclassifiedDocumentCount,
        @Schema(description = "기존 사용자 정의 폴더에 배정된 저장 문서 수", example = "0")
        long legacyFolderDocumentCount,
        @Schema(description = "고정 폴더별 문서 수 (청약·주거, 정부 지원정책, 교육 순)")
        List<FolderOverview> folders
) {
    @Schema(description = "폴더별 저장 문서 통계")
    public record FolderOverview(
            @Schema(description = "폴더 ID", example = "1")
            long folderId,
            @Schema(description = "고정 폴더 카테고리", example = "HOUSING")
            SourceFolderCategory category,
            @Schema(description = "폴더 이름", example = "청약·주거")
            String folderName,
            @Schema(description = "해당 폴더의 저장 문서 수", example = "12")
            long documentCount
    ) { }
}
