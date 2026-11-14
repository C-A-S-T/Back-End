package com.example.cast.research.dto.response;

import com.example.cast.claim.entity.EvidenceRelation;
import com.example.cast.claim.entity.VerificationStatus;
import com.example.cast.document.entity.SourceType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "리서치 완료 결과의 주장별 최종 검증 근거")
public record ResearchEvidenceResponse(
        Long taskId,
        List<ClaimItem> claims
) {
    public record ClaimItem(
            Long claimId,
            Integer claimIndex,
            String claimContent,
            VerificationStatus verificationStatus,
            List<EvidenceItem> evidences
    ) {}

    public record EvidenceItem(
            Long evidenceId,
            String evidenceText,
            EvidenceRelation evidenceRelation,
            Long documentId,
            String documentTitle,
            String sourceName,
            String sourceUrl,
            SourceType sourceType,
            Integer pageStart,
            Integer pageEnd,
            String sectionTitle,
            boolean isScrapped,
            Long scrapId
    ) {}
}
