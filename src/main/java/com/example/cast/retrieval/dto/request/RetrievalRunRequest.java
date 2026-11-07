package com.example.cast.retrieval.dto.request;
import com.example.cast.retrieval.entity.RetrievalRunType;
import jakarta.validation.constraints.*;
/** 검색 유형에 맞는 Claim/회차 조합만 허용한다. */
public record RetrievalRunRequest(@NotNull @Positive Long taskId, @Positive Long claimId,
        @NotNull RetrievalRunType type, int attempt, @NotBlank String query) {
    @AssertTrue(message = "검색 유형과 회차 조합이 올바르지 않습니다.")
    public boolean isCombinationValid() {
        if (type == null) return false;
        return switch (type) {
            case INITIAL -> claimId == null && attempt == 0;
            case CLAIM_SEARCH -> claimId != null && attempt == 0;
            case RETRY -> claimId != null && attempt >= 1 && attempt <= 3;
        };
    }
}
