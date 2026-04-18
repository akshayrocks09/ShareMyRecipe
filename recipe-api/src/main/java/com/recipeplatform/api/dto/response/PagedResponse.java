package com.recipeplatform.api.dto.response;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.domain.Page;

import java.util.List;

@Data
@Builder
public class PagedResponse<T> {

    private List<T> data;
    private Meta meta;

    @Data
    @Builder
    public static class Meta {
        private int page;
        private int pageSize;
        private long total;
        private int totalPages;
        private boolean hasNext;
        private boolean hasPrevious;
    }

    public static <T> PagedResponse<T> of(Page<T> page) {
        return PagedResponse.<T>builder()
                .data(page.getContent())
                .meta(Meta.builder()
                        .page(page.getNumber() + 1)
                        .pageSize(page.getSize())
                        .total(page.getTotalElements())
                        .totalPages(page.getTotalPages())
                        .hasNext(page.hasNext())
                        .hasPrevious(page.hasPrevious())
                        .build())
                .build();
    }

    public static <T> PagedResponse<T> of(List<T> content, int page, int pageSize, long total) {
        int totalPages = (int) Math.ceil((double) total / pageSize);
        return PagedResponse.<T>builder()
                .data(content)
                .meta(Meta.builder()
                        .page(page)
                        .pageSize(pageSize)
                        .total(total)
                        .totalPages(totalPages)
                        .hasNext(page < totalPages)
                        .hasPrevious(page > 1)
                        .build())
                .build();
    }
}
