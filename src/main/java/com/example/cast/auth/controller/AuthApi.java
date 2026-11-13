
package com.example.cast.auth.controller;

import com.example.cast.auth.dto.request.LoginRequest;
import com.example.cast.auth.dto.request.SignupRequest;
import com.example.cast.auth.dto.response.AuthResponse;
import com.example.cast.global.response.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 이메일 인증 API의 Swagger 계약.
 *
 * 실제 비즈니스 로직은 AuthController와 AuthService에 위치한다.
 */
@Tag(
        name = "Auth",
        description = "이메일 회원가입 및 JWT 로그인"
)
public interface AuthApi {

    @Operation(
            summary = "이메일 회원가입",
            description = """
                    이메일의 앞뒤 공백을 제거하고 소문자로 정규화합니다.
                    비밀번호는 영문·숫자·ASCII 특수문자를 각각 포함해
                    8자 이상, UTF-8 기준 72바이트 이하여야 합니다.

                    가입 직후 자동 로그인되어 Access Token을 반환합니다.
                    이메일 중복은 HTTP 409입니다.
                    Refresh Token과 이메일 인증은 제공하지 않습니다.
                    """
    )
    @SecurityRequirements
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201",
                    description = "회원가입 완료 및 JWT 발급",
                    useReturnTypeSchema = true,
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    name = "SIGNUP_SUCCESS",
                                    value = SIGNUP_SUCCESS
                            )
                    )
            ),

            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "입력값 또는 JSON 형식 오류",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(
                                    implementation = ApiResponse.class
                            ),
                            examples = {
                                    @ExampleObject(
                                            name = "INVALID_REQUEST",
                                            value = INVALID_REQUEST
                                    ),
                                    @ExampleObject(
                                            name = "MALFORMED_JSON",
                                            value = MALFORMED_JSON
                                    )
                            }
                    )
            ),

            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "중복 이메일 또는 데이터 충돌",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(
                                    implementation = ApiResponse.class
                            ),
                            examples = {
                                    @ExampleObject(
                                            name = "EMAIL_ALREADY_EXISTS",
                                            value = EMAIL_ALREADY_EXISTS
                                    ),
                                    @ExampleObject(
                                            name = "DATA_CONFLICT",
                                            value = DATA_CONFLICT
                                    )
                            }
                    )
            )
    })
    @PostMapping("/signup")
    ResponseEntity<ApiResponse<AuthResponse>> signup(
            @Valid @RequestBody SignupRequest request
    );

    @Operation(
            summary = "이메일 로그인",
            description = """
                    이메일과 비밀번호를 검증하고
                    60분 유효한 Access Token을 반환합니다.

                    이메일 미존재와 비밀번호 불일치는
                    동일한 HTTP 401 응답입니다.

                    비밀번호 검증에 성공한 정지·탈퇴 계정은
                    HTTP 403을 반환합니다.

                    기존 계정 로그인에는 새로운 회원가입 비밀번호
                    조합 규칙을 소급하지 않습니다.
                    """
    )
    @SecurityRequirements
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "로그인 성공 및 JWT 발급",
                    useReturnTypeSchema = true,
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    name = "LOGIN_SUCCESS",
                                    value = LOGIN_SUCCESS
                            )
                    )
            ),

            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "입력값 또는 JSON 형식 오류",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(
                                    implementation = ApiResponse.class
                            ),
                            examples = {
                                    @ExampleObject(
                                            name = "INVALID_REQUEST",
                                            value = INVALID_REQUEST
                                    ),
                                    @ExampleObject(
                                            name = "MALFORMED_JSON",
                                            value = MALFORMED_JSON
                                    )
                            }
                    )
            ),

            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "이메일 또는 비밀번호 불일치",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(
                                    implementation = ApiResponse.class
                            ),
                            examples = @ExampleObject(
                                    name = "INVALID_CREDENTIALS",
                                    value = INVALID_CREDENTIALS
                            )
                    )
            ),

            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "정지 또는 탈퇴 계정",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(
                                    implementation = ApiResponse.class
                            ),
                            examples = @ExampleObject(
                                    name = "ACCOUNT_DISABLED",
                                    value = ACCOUNT_DISABLED
                            )
                    )
            )
    })
    @PostMapping("/login")
    ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request
    );

    // ==========================================
    // Swagger 응답 JSON 예시
    // ==========================================

    String SIGNUP_SUCCESS = """
            {
              "isSuccess": true,
              "code": "COMMON201",
              "message": "회원가입이 완료되었습니다.",
              "timestamp": "2026-11-14T15:30:00",
              "result": {
                "userId": 1,
                "email": "user@example.com",
                "accessToken": "<발급된 JWT>",
                "tokenType": "Bearer",
                "expiresIn": 3600
              }
            }
            """;

    String LOGIN_SUCCESS = """
            {
              "isSuccess": true,
              "code": "COMMON200",
              "message": "로그인에 성공했습니다.",
              "timestamp": "2026-11-14T15:30:00",
              "result": {
                "userId": 1,
                "email": "user@example.com",
                "accessToken": "<발급된 JWT>",
                "tokenType": "Bearer",
                "expiresIn": 3600
              }
            }
            """;

    String INVALID_REQUEST = """
            {
              "isSuccess": false,
              "code": "COMMON400",
              "message": "잘못된 요청입니다.",
              "timestamp": "2026-11-14T15:30:00",
              "result": {}
            }
            """;

    String MALFORMED_JSON = """
            {
              "isSuccess": false,
              "code": "COMMON400",
              "message": "요청 본문을 읽을 수 없습니다.",
              "timestamp": "2026-11-14T15:30:00",
              "result": {}
            }
            """;

    String EMAIL_ALREADY_EXISTS = """
            {
              "isSuccess": false,
              "code": "AUTH4091",
              "message": "이미 사용 중인 이메일입니다.",
              "timestamp": "2026-11-14T15:30:00",
              "result": {}
            }
            """;

    String DATA_CONFLICT = """
            {
              "isSuccess": false,
              "code": "COMMON409",
              "message": "이미 존재하거나 충돌하는 데이터입니다.",
              "timestamp": "2026-11-14T15:30:00",
              "result": {}
            }
            """;

    String INVALID_CREDENTIALS = """
            {
              "isSuccess": false,
              "code": "AUTH4011",
              "message": "이메일 또는 비밀번호가 올바르지 않습니다.",
              "timestamp": "2026-11-14T15:30:00",
              "result": {}
            }
            """;

    String ACCOUNT_DISABLED = """
            {
              "isSuccess": false,
              "code": "AUTH4031",
              "message": "사용할 수 없는 계정입니다.",
              "timestamp": "2026-11-14T15:30:00",
              "result": {}
            }
            """;
}
