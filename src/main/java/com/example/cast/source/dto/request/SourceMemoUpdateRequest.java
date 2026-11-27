package com.example.cast.source.dto.request;

import com.example.cast.global.code.GlobalErrorCode;
import com.example.cast.global.exception.BusinessException;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

@Schema(description = "문서별 개인 메모 수정. memo 키는 필수이며 null 또는 빈 문자열은 삭제입니다.")
public record SourceMemoUpdateRequest(
        @Schema(description = "최대 1000 UTF-16 코드 단위", nullable = true, maxLength = 1000,
                requiredMode = Schema.RequiredMode.REQUIRED) String memo
) {
    public static SourceMemoUpdateRequest from(Map<String, Object> body) {
        if (body == null || body.size() != 1 || !body.containsKey("memo")) {
            throw new BusinessException(GlobalErrorCode.INVALID_REQUEST);
        }
        Object value = body.get("memo");
        if (value != null && (!(value instanceof String text) || text.length() > 1000)) {
            throw new BusinessException(GlobalErrorCode.INVALID_REQUEST);
        }
        return new SourceMemoUpdateRequest((String) value);
    }
}
