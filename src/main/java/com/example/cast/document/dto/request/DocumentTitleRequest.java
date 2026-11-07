package com.example.cast.document.dto.request;
import jakarta.validation.constraints.NotBlank;
/** 문서 제목 변경 요청. */
public record DocumentTitleRequest(@NotBlank String title) {}
