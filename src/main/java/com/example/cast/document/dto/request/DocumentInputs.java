package com.example.cast.document.dto.request;
import com.example.cast.document.entity.*;
import jakarta.validation.constraints.*;
/** 서비스 경계의 입력 계약. 엔티티 생성/변경 전에 검증한다. */
public final class DocumentInputs {
    private DocumentInputs() {}
    public record Chunk(@PositiveOrZero int index, @NotBlank String content) {}
    public record PageRange(@Positive Integer start, @Positive Integer end) {
        @AssertTrue public boolean isOrdered() { return start == null || end == null || start <= end; }
    }
    public record Source(@Size(max=255) String name, String url, String path,
            @Size(max=100) String version, @NotNull SourceType type) {}
}
