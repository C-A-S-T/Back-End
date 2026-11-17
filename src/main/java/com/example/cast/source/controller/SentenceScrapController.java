package com.example.cast.source.controller;

import com.example.cast.auth.security.AuthenticatedUser;
import com.example.cast.claim.entity.VerificationStatus;
import com.example.cast.document.entity.SourceType;
import com.example.cast.global.code.CommonSuccessCode;
import com.example.cast.global.response.ApiResponse;
import com.example.cast.source.dto.request.SentenceScrapCreateRequest;
import com.example.cast.source.dto.response.SentenceScrapListResponse;
import com.example.cast.source.dto.response.SentenceScrapSaveResponse;
import com.example.cast.source.service.SentenceScrapService;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sources")
@RequiredArgsConstructor
public class SentenceScrapController implements SentenceScrapApi {
    private final SentenceScrapService service;

    @Override
    public ResponseEntity<ApiResponse<SentenceScrapSaveResponse>> save(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody SentenceScrapCreateRequest request) {
        var saved = service.save(user.userId(), request.evidenceId());
        return ApiResponse.success(saved.created() ? CommonSuccessCode.CREATED : CommonSuccessCode.OK,
                saved.response());
    }

    @Override
    public ResponseEntity<ApiResponse<SentenceScrapListResponse>> list(
            @AuthenticationPrincipal AuthenticatedUser user,
            String keyword, VerificationStatus verificationStatus, SourceType sourceType,
            Long folderId, int page, int size) {
        return ApiResponse.success(CommonSuccessCode.OK,
                service.list(user.userId(), keyword, verificationStatus, sourceType, folderId, page, size));
    }

    @Override
    public ResponseEntity<ApiResponse<Map<String, Object>>> delete(
            @AuthenticationPrincipal AuthenticatedUser user, Long scrapId) {
        service.delete(user.userId(), scrapId);
        return ApiResponse.success(CommonSuccessCode.OK);
    }
}
