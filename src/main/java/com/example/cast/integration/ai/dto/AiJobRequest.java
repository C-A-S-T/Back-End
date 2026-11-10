package com.example.cast.integration.ai.dto;
import java.util.UUID;
/** AI 서버 POST /v1/research-jobs 계약. jobId가 중복이면 같은 작업으로 처리한다. */
public record AiJobRequest(UUID jobId, Long taskId, String question) {}
