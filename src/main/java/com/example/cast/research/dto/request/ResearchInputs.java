package com.example.cast.research.dto.request;
import com.example.cast.research.entity.*;
import jakarta.validation.constraints.*;
/** 서비스 경계의 입력 계약. 엔티티 생성/변경 전에 검증한다. */
public final class ResearchInputs {
    private ResearchInputs() {}
    public record Query(@NotBlank String question) {}
    public record Progress(@Min(1) @Max(6) Short step, String summary) {}
    public record FinalAnswer(@NotBlank String content, String summary) {}
}
