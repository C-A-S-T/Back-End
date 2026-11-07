package com.example.cast.claim.dto.request;
import com.example.cast.claim.entity.*;
import jakarta.validation.constraints.*;
/** 서비스 경계의 입력 계약. 엔티티 생성/변경 전에 검증한다. */
public final class ClaimInputs {
    private ClaimInputs() {}
    public record EvidenceInput(@NotBlank String text, @NotNull EvidenceRelation relation) {}
}
