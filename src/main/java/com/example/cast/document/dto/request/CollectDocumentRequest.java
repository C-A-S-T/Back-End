package com.example.cast.document.dto.request;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
/** 동일성은 인코딩 변환 전 원문 바이트 전체의 SHA-256으로 판단한다. */
public record CollectDocumentRequest(@NotBlank String title, @NotEmpty byte[] content) {
    public CollectDocumentRequest { content = content == null ? null : content.clone(); }
    @Override public byte[] content() { return content == null ? null : content.clone(); }
}
