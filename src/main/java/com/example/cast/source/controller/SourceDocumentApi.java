package com.example.cast.source.controller;

import com.example.cast.auth.security.AuthenticatedUser;
import com.example.cast.global.config.SwaggerConfig;
import com.example.cast.global.response.ApiResponse;
import com.example.cast.source.dto.response.SourceDocumentDetailResponse;
import com.example.cast.source.dto.response.SourceDocumentListResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = "Source Archive", description = "사용자의 출처보관함 조회")
public interface SourceDocumentApi {
    @Operation(
            summary = "저장 문서 전체·폴더별 목록 조회",
            description = """
                    로그인 사용자의 자동·수동 저장 문서를 최신 저장순으로 조회합니다.
                    folderId를 생략하면 미분류 및 기존 폴더 문서를 포함한 전체 목록을 반환합니다.
                    folderId를 전달하면 사용자가 소유한 고정 폴더의 문서만 반환합니다.
                    keyword는 문서 제목 또는 발행 기관에 대한 대소문자 무시 부분 검색입니다(최대 100자).
                    sourceType, saveMethod, favorite 필터를 선택적으로 조합할 수 있습니다. 모든 조건은 AND로 결합합니다.
                    page는 0부터 시작하며 size는 1~100입니다. 기본값은 page=0, size=20입니다.
                    """,
            security = @SecurityRequirement(name = SwaggerConfig.JWT_SCHEME)
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "저장 문서 목록 조회 성공",
                    useReturnTypeSchema = true,
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(name = "DOCUMENT_LIST_SUCCESS", value = DOCUMENT_LIST_SUCCESS))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "검색어, 필터, 페이지, 크기, 폴더 ID 요청값 오류",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(name = "INVALID_REQUEST", value = INVALID_REQUEST))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "JWT 미입력·만료·검증 실패",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(name = "UNAUTHORIZED", value = UNAUTHORIZED))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "존재하지 않거나 소유하지 않은 고정 폴더",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(name = "FOLDER_NOT_FOUND", value = FOLDER_NOT_FOUND))
            )
    })
    @GetMapping("/documents")
    ResponseEntity<ApiResponse<SourceDocumentListResponse>> listDocuments(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @Parameter(description = "고정 폴더 ID (생략 시 전체 조회)", example = "3")
            @RequestParam(required = false) Long folderId,
            @Parameter(description = "문서 제목·발행 기관 통합 검색어 (부분 일치, 최대 100자)", example = "청년")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "출처 유형 필터", example = "GOVERNMENT")
            @RequestParam(required = false) com.example.cast.document.entity.SourceType sourceType,
            @Parameter(description = "최초 저장 방식 필터", example = "MANUAL")
            @RequestParam(required = false) com.example.cast.source.entity.SaveMethod saveMethod,
            @Parameter(description = "즐겨찾기 여부 필터 (생략 시 전체)", example = "true")
            @RequestParam(required = false) Boolean favorite,
            @Parameter(description = "0부터 시작하는 페이지 번호", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지당 문서 수 (1~100)", example = "20")
            @RequestParam(defaultValue = "20") int size
    );

    @Operation(
            summary = "저장 문서 상세 조회",
            description = """
                    로그인 사용자가 보관한 자동·수동 저장 문서의 메타데이터를 조회합니다.
                    savedSourceId는 사용자의 저장 기록 ID이며, 다른 사용자 소유 기록은 404를 반환합니다.
                    문서 메타데이터, 최신 완료 검증 근거, 관련 리서치 및 사용자 스크랩 문장을 반환합니다.
                    각 관련 목록은 최대 20건이며 전체 건수와 추가 존재 여부를 함께 제공합니다.
                    진행 중인 최초 관련 리서치는 포함할 수 있지만 그 밖의 관련 리서치는 완료된 작업만 반환합니다.
                    과거 검증 시도에서 스크랩한 문장은 검증 근거 목록에서 빠져도 스크랩 목록에 유지됩니다.
                    원문 전체, AI 문서 자체 요약 및 최신 버전 판정은 반환하지 않습니다.
                    originTaskId는 해당 문서를 최초 저장하게 된 연구 작업 ID이며 없으면 null입니다.
                    """,
            security = @SecurityRequirement(name = SwaggerConfig.JWT_SCHEME)
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "저장 문서 상세 조회 성공",
                    useReturnTypeSchema = true,
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(name = "DOCUMENT_DETAIL_SUCCESS", value = DOCUMENT_DETAIL_SUCCESS))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "저장 기록 ID 형식 또는 값 오류",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(name = "INVALID_REQUEST", value = INVALID_REQUEST))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "JWT 미입력·만료·검증 실패",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(name = "UNAUTHORIZED", value = UNAUTHORIZED))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "존재하지 않거나 소유하지 않은 저장 기록",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(name = "DOCUMENT_NOT_FOUND", value = FOLDER_NOT_FOUND))
            )
    })
    @GetMapping("/documents/{savedSourceId}")
    ResponseEntity<ApiResponse<SourceDocumentDetailResponse>> getDocumentDetail(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @Parameter(description = "출처보관함 저장 기록 ID", example = "12")
            @PathVariable Long savedSourceId
    );

    String DOCUMENT_LIST_SUCCESS = """
            {
              "isSuccess": true,
              "code": "COMMON200",
              "message": "성공입니다.",
              "timestamp": "2026-11-14T17:30:00",
              "result": {
                "items": [{
                  "savedSourceId": 12,
                  "documentId": 31,
                  "documentTitle": "2026년 청년 주거 지원사업 안내",
                  "sourceName": "공공기관",
                  "sourceUrl": "https://example.go.kr/document",
                  "publishedAt": "2026-10-15",
                  "sourceType": "GOVERNMENT",
                  "reliabilityLevel": "HIGH",
                  "saveMethod": "AUTO",
                  "folderId": 3,
                  "favorite": false,
                  "savedAt": "2026-11-14T08:30:00Z"
                }],
                "page": 0,
                "size": 20,
                "totalElements": 1,
                "totalPages": 1,
                "hasNext": false
              }
            }
            """;

    String DOCUMENT_DETAIL_SUCCESS = """
            {
              "isSuccess": true,
              "code": "COMMON200",
              "message": "성공입니다.",
              "timestamp": "2026-11-14T18:30:00",
              "result": {
                "savedSourceId": 12,
                "documentId": 31,
                "documentTitle": "가상 A 청년월세지원 사업 공고문",
                "sourceName": "가상 청년지원센터",
                "sourceUrl": "https://example.go.kr/document",
                "publishedAt": "2026-10-01",
                "effectiveFrom": "2026-11-01",
                "effectiveTo": null,
                "documentVersion": "2026-1",
                "sourceType": "GOVERNMENT",
                "reliabilityLevel": "HIGH",
                "saveMethod": "AUTO",
                "folderId": null,
                "favorite": false,
                "originTaskId": 7,
                "savedAt": "2026-11-14T08:30:00Z",
                "verificationEvidences": [{
                  "evidenceId": 45,
                  "evidenceText": "월 최대 20만 원을 지원한다.",
                  "evidenceRelation": "SUPPORTS",
                  "claimId": 18,
                  "claimContent": "청년월세 지원금은 월 최대 20만 원이다.",
                  "verificationStatus": "SUPPORTED",
                  "taskId": 7,
                  "pageStart": 3,
                  "pageEnd": 3,
                  "sectionTitle": "지원 금액",
                  "isScrapped": true,
                  "scrapId": 5
                }],
                "verificationEvidenceTotalCount": 1,
                "verificationEvidenceHasMore": false,
                "relatedResearches": [{
                  "taskId": 7,
                  "researchTitle": "2026 청년월세 지원 조건",
                  "question": "청년월세 지원금은 얼마인가요?",
                  "summaryContent": "월 최대 20만 원의 지원을 받을 수 있습니다.",
                  "completedAt": "2026-11-14T10:00:00Z"
                }],
                "relatedResearchTotalCount": 1,
                "relatedResearchHasMore": false,
                "scrappedSentences": [{
                  "scrapId": 5,
                  "evidenceId": 45,
                  "evidenceText": "월 최대 20만 원을 지원한다.",
                  "claimContent": "청년월세 지원금은 월 최대 20만 원이다.",
                  "scrappedAt": "2026-11-14T11:00:00Z"
                }],
                "scrappedSentenceTotalCount": 1,
                "scrappedSentenceHasMore": false
              }
            }
            """;

    String INVALID_REQUEST = """
            {"isSuccess":false,"code":"COMMON400","message":"잘못된 요청입니다.","timestamp":"2026-11-14T17:30:00","result":{}}
            """;

    String UNAUTHORIZED = """
            {"isSuccess":false,"code":"COMMON401","message":"인증이 필요합니다.","timestamp":"2026-11-14T17:30:00","result":{}}
            """;

    String FOLDER_NOT_FOUND = """
            {"isSuccess":false,"code":"COMMON404","message":"요청한 리소스를 찾을 수 없습니다.","timestamp":"2026-11-14T17:30:00","result":{}}
            """;
}
