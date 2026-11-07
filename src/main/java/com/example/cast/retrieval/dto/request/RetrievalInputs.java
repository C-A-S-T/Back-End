package com.example.cast.retrieval.dto.request;
import com.example.cast.retrieval.entity.*;
import jakarta.validation.constraints.*;
/** 서비스 경계의 입력 계약. 엔티티 생성/변경 전에 검증한다. */
public final class RetrievalInputs {
    private RetrievalInputs() {}
    public record Result(@Min(1) int rank, Double score) {}
}
