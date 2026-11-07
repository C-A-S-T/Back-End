package com.example.cast.evaluation.dto.request;
import com.example.cast.evaluation.entity.*;
import jakarta.validation.constraints.*;
/** 서비스 경계의 입력 계약. 엔티티 생성/변경 전에 검증한다. */
public final class EvaluationInputs {
    private EvaluationInputs() {}
    public record Create(@NotBlank String question, String referenceAnswer,
            @NotNull java.util.List<@NotNull @jakarta.validation.Valid GoldClaim> claims,
            @NotNull java.util.List<@NotNull @Positive Long> chunkIds, @Size(max=100) String category) {}
    public record GoldClaim(@NotNull com.example.cast.claim.entity.ClaimType type,
            @NotBlank String content, @NotEmpty java.util.Map<@NotBlank String, @NotNull Object> value) {}
}
