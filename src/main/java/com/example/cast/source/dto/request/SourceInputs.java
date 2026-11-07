package com.example.cast.source.dto.request;
import com.example.cast.source.entity.*;
import jakarta.validation.constraints.*;
/** 서비스 경계의 입력 계약. 엔티티 생성/변경 전에 검증한다. */
public final class SourceInputs {
    private SourceInputs() {}
    public record Folder(@NotBlank @Size(max=120) String name) {}
    public record Move(@Positive Long folderId) {}
}
