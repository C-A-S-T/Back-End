package com.example.cast.source.controller;

import com.example.cast.auth.security.AuthenticatedUser;
import com.example.cast.global.code.CommonSuccessCode;
import com.example.cast.global.response.ApiResponse;
import com.example.cast.source.dto.response.SourceDocumentDetailResponse;
import com.example.cast.source.dto.response.SourceDocumentListResponse;
import com.example.cast.source.service.SourceDocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sources")
@RequiredArgsConstructor
public class SourceDocumentController implements SourceDocumentApi {
    private final SourceDocumentService documentService;

    @Override
    public ResponseEntity<ApiResponse<SourceDocumentListResponse>> listDocuments(
            @AuthenticationPrincipal AuthenticatedUser user,
            Long folderId,
            String keyword,
            com.example.cast.document.entity.SourceType sourceType,
            com.example.cast.source.entity.SaveMethod saveMethod,
            Boolean favorite,
            int page,
            int size
    ) {
        return ApiResponse.success(CommonSuccessCode.OK,
                documentService.listDocuments(user.userId(), folderId, keyword,
                        sourceType, saveMethod, favorite, page, size));
    }

    @Override
    public ResponseEntity<ApiResponse<SourceDocumentDetailResponse>> getDocumentDetail(
            @AuthenticationPrincipal AuthenticatedUser user,
            Long savedSourceId
    ) {
        return ApiResponse.success(CommonSuccessCode.OK,
                documentService.getDocumentDetail(user.userId(), savedSourceId));
    }
}
