package com.example.cast.source.dto.response;

import com.example.cast.claim.entity.EvidenceRelation;
import com.example.cast.claim.entity.VerificationStatus;
import com.example.cast.document.entity.SourceType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

@Schema(description = "출처보관함 스크랩 문장 카드 목록")
public record SentenceScrapListResponse(
        List<ScrapItem> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {
    @Schema(description = "사용자가 스크랩한 Evidence 및 연결된 검증/출처 정보")
    public record ScrapItem(
            Long scrapId,
            Long evidenceId,
            String evidenceText,
            Long claimId,
            String claimContent,
            VerificationStatus verificationStatus,
            EvidenceRelation evidenceRelation,
            Long documentId,
            String documentTitle,
            String sourceName,
            String sourceUrl,
            SourceType sourceType,
            Long folderId,
            Long savedSourceId,
            Instant scrappedAt
    ) {}
}
