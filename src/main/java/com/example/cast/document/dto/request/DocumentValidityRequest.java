package com.example.cast.document.dto.request;
import java.time.LocalDate;
import jakarta.validation.constraints.AssertTrue;
/** null 경계는 시작 또는 종료 제한이 없다는 의미다. */
public record DocumentValidityRequest(LocalDate publishedAt, LocalDate effectiveFrom, LocalDate effectiveTo) {
    @AssertTrue(message = "적용 종료일은 시작일보다 빠를 수 없습니다.")
    public boolean isEffectivePeriodValid() {
        return effectiveFrom == null || effectiveTo == null || !effectiveTo.isBefore(effectiveFrom);
    }
}
