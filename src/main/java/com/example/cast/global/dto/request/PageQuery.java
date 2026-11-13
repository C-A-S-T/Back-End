
package com.example.cast.global.dto.request;

import com.example.cast.global.code.GlobalErrorCode;
import com.example.cast.global.exception.BusinessException;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;

/**
 * 공통 페이지 조회 요청.
 *
 * 외부 API: page는 1부터 시작한다.
 * Spring Data JPA: page는 0부터 시작한다.
 */
@Schema(description = "공통 페이지 조회 요청")
public record PageQuery(

        @Schema(
                description = "페이지 번호 (1부터 시작)",
                example = "1",
                defaultValue = "1"
        )
        @Min(1)
        Integer page,

        @Schema(
                description = "페이지당 조회 개수 (1~100)",
                example = "20",
                defaultValue = "20"
        )
        @Min(1)
        @Max(100)
        Integer size

) {

    /**
     * 일반 목록 기본값: page=1, size=20
     */
    public PageQuery {
        if (page == null) {
            page = 1;
        }

        if (size == null) {
            size = 20;
        }
    }

    /**
     * 스크랩 목록 기본값: page=1, size=8
     */
    public static PageQuery forScraps(
            Integer page,
            Integer size
    ) {
        return new PageQuery(
                page,
                size == null ? 8 : size
        );
    }

    /**
     * Spring Data JPA PageRequest 변환.
     */
    public PageRequest toPageRequest() {
        if (page < 1 || size < 1 || size > 100) {
            throw new BusinessException(
                    GlobalErrorCode.INVALID_REQUEST
            );
        }

        return PageRequest.of(
                page - 1,
                size
        );
    }
}
