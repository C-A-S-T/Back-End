package com.example.cast.claim.dto.request;
import com.example.cast.claim.entity.ClaimType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotEmpty;
import java.util.Map;
/** 신규/수정 Claim은 유형과 정규화 값을 함께 전달한다. */
public record ClaimRequest(@NotBlank String content, @NotNull ClaimType type,
        @NotEmpty Map<@NotBlank String, @NotNull Object> value) {}
