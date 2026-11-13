package com.example.cast.integration.ai.controller;
import com.example.cast.integration.ai.dto.*;
import com.example.cast.integration.ai.service.AiResultService;
import com.example.cast.global.code.SuccessCode;
import com.example.cast.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
/** 전용 서비스 인증을 통과한 AI 서버의 결과만 수신한다. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/ai/research-tasks")
public class AiResultController {
    private final AiResultService results;
    private enum ResultCode implements SuccessCode {
        ACCEPTED;
        public HttpStatus getStatus() { return HttpStatus.OK; }
        public String getMessage() { return "AI 결과가 반영되었습니다."; }
    }
    @PostMapping("/{taskId}/result")
    public ResponseEntity<ApiResponse<AiResultReceipt>> accept(@PathVariable Long taskId,
            @Valid @RequestBody AiResultRequest request) {
        return ApiResponse.success(ResultCode.ACCEPTED, results.accept(taskId, request));
    }
}
