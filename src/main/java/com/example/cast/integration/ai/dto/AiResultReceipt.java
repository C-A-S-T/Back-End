package com.example.cast.integration.ai.dto;
import com.example.cast.research.entity.ResearchTaskStatus;
/** 중복 전달에도 저장된 결과 식별자를 그대로 반환한다. */
public record AiResultReceipt(Long taskId, Long answerId, ResearchTaskStatus status) {}
