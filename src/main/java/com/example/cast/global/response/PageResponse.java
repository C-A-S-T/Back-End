package com.example.cast.global.response;
import java.util.List;
import org.springframework.data.domain.Page;
/** 외부 페이지 번호는 1부터 시작한다. 엔티티 대신 DTO로 변환한 Page를 전달한다. */
public record PageResponse<T>(List<T> content, int page, int size,
        long totalElements, int totalPages, boolean hasNext) {
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(List.copyOf(page.getContent()), page.getNumber() + 1,
                page.getSize(), page.getTotalElements(), page.getTotalPages(), page.hasNext());
    }
}
