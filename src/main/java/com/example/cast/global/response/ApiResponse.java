
package com.example.cast.global.response;

import com.example.cast.global.code.ErrorCode;
import com.example.cast.global.code.SuccessCode;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Objects;

/**
 * C.A.S.T. 공통 API 응답.
 *
 * JSON:
 * {
 *   "isSuccess": true,
 *   "code": "COMMON200",
 *   "message": "성공입니다.",
 *   "timestamp": "2026-11-14T15:30:00",
 *   "result": {}
 * }
 */
@Schema(description = "C.A.S.T. 공통 API 응답")
@JsonPropertyOrder({
        "isSuccess",
        "code",
        "message",
        "timestamp",
        "result"
})
public record ApiResponse<T>(

        @JsonProperty("isSuccess")
        boolean isSuccess,

        String code,

        String message,

        String timestamp,

        T result

) {

    private static final ZoneId SEOUL_ZONE =
            ZoneId.of("Asia/Seoul");

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern(
                    "uuuu-MM-dd'T'HH:mm:ss"
            );

    private static String currentTimestamp() {
        return LocalDateTime.now(SEOUL_ZONE)
                .format(TIMESTAMP_FORMAT);
    }

    /**
     * 데이터가 포함된 성공 응답.
     */
    public static <T> ResponseEntity<ApiResponse<T>> success(
            SuccessCode code,
            T result
    ) {
        Objects.requireNonNull(code, "SuccessCode is required");
        Objects.requireNonNull(
                result,
                "Use success(code) for an empty response"
        );

        return ResponseEntity
                .status(code.getStatus())
                .body(new ApiResponse<>(
                        true,
                        code.getCode(),
                        code.getMessage(),
                        currentTimestamp(),
                        result
                ));
    }

    /**
     * 반환 데이터가 없는 성공 응답.
     */
    public static ResponseEntity<ApiResponse<Map<String, Object>>> success(
            SuccessCode code
    ) {
        return success(code, Map.of());
    }

    /**
     * HTTP 상태에 맞는 실패 응답.
     */
    public static ResponseEntity<ApiResponse<Map<String, Object>>> error(
            ErrorCode code
    ) {
        return ResponseEntity
                .status(code.getStatus())
                .body(errorBody(code));
    }

    /**
     * Security Filter 등 MVC 외부에서 사용하는 실패 응답.
     */
    public static ApiResponse<Map<String, Object>> errorBody(
            ErrorCode code
    ) {
        Objects.requireNonNull(code, "ErrorCode is required");

        return new ApiResponse<>(
                false,
                code.getCode(),
                code.getMessage(),
                currentTimestamp(),
                Map.of()
        );
    }
}
