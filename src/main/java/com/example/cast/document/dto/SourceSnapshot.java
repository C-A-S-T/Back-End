package com.example.cast.document.dto;
import com.example.cast.document.entity.SourceType;
import com.example.cast.document.entity.ReliabilityLevel;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
/** AI가 보내는 출처 메타데이터. 내부 파일 경로/원문/임베딩은 받지 않는다. */
public record SourceSnapshot(@NotNull @Positive Long aiDocumentId, @NotBlank String title,
        @Size(max=255) String sourceName, String sourceUrl, LocalDate publishedAt,
        LocalDate effectiveFrom, LocalDate effectiveTo, @Size(max=100) String version,
        @NotNull SourceType sourceType, ReliabilityLevel reliabilityLevel) {
    @AssertTrue(message = "적용 기간이 올바르지 않습니다.")
    public boolean isPeriodValid() {
        return effectiveFrom == null || effectiveTo == null || !effectiveTo.isBefore(effectiveFrom);
    }
}
