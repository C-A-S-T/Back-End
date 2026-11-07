package com.example.cast.claim.dto.request;
import com.example.cast.claim.entity.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.AssertTrue;
/** 진행 중 상태는 판정 결과로 저장할 수 없다. confidence는 선택 값이다. */
public record VerificationResultRequest(@NotNull VerificationStatus status, String reason,
        @NotNull VerificationMethod method, VerificationStatus ruleResult,
        VerificationStatus llmResult, Double confidence) {
    @AssertTrue(message = "검증 결과 조합이 올바르지 않습니다.")
    public boolean isResultValid() {
        if (status == VerificationStatus.IN_PROGRESS || ruleResult == VerificationStatus.IN_PROGRESS
                || llmResult == VerificationStatus.IN_PROGRESS) return false;
        if (confidence != null && (!Double.isFinite(confidence) || confidence < 0 || confidence > 1)) return false;
        if (method == null) return false;
        return switch (method) {
            case RULE -> ruleResult != null && llmResult == null && status == ruleResult;
            case LLM -> llmResult != null && ruleResult == null && status == llmResult;
            case HYBRID -> ruleResult != null && llmResult != null;
        };
    }
}
