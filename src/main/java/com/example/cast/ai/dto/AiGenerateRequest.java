
package com.example.cast.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AiGenerateRequest(
        @JsonProperty("task_id") Long taskId,
        String question
) {
}
