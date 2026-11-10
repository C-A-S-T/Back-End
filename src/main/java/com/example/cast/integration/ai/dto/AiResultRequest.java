package com.example.cast.integration.ai.dto;
import com.example.cast.claim.entity.*;
import com.example.cast.claim.dto.request.VerificationResultRequest;
import com.example.cast.document.dto.SourceSnapshot;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
/** 최종 업무 결과만 교환한다. 검색 후보/평가 데이터/전체 청크는 AI DB에 남긴다. */
public record AiResultRequest(@NotNull UUID jobId, @NotNull Outcome outcome,
        String answer, String summary, String errorMessage,
        @NotNull @Size(max=500) List<@NotNull @Valid ClaimResult> claims) {
    public enum Outcome { SUCCESS, FAILURE }
    @AssertTrue(message = "성공/실패 결과의 내용이 올바르지 않습니다.")
    public boolean isOutcomeValid() {
        if (outcome == null || claims == null) return false;
        return outcome == Outcome.SUCCESS
                ? answer != null && !answer.isBlank() && errorMessage == null
                : answer == null && summary == null && claims.isEmpty()
                    && errorMessage != null && !errorMessage.isBlank();
    }
    public record ClaimResult(@NotBlank String content, @NotNull ClaimType type,
            @NotEmpty Map<@NotBlank String, @NotNull Object> value,
            @NotNull @Valid VerificationResultRequest verification, @Positive Long aiRunId,
            @NotNull @Size(max=100) List<@NotNull @Valid Citation> citations) {}
    public record Citation(@NotNull @Valid SourceSnapshot source, @Positive Long aiChunkId,
            @NotBlank String text, @NotNull EvidenceRelation relation,
            @Positive Integer pageStart, @Positive Integer pageEnd, String sectionTitle) {
        @AssertTrue(message = "인용 페이지 범위가 올바르지 않습니다.")
        public boolean isPageRangeValid() { return pageStart == null || pageEnd == null || pageEnd >= pageStart; }
    }
}
