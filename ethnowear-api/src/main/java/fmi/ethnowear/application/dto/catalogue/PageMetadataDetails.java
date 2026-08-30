package fmi.ethnowear.application.dto.catalogue;

import org.springframework.data.domain.Page;

public record PageMetadataDetails(
        int number,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {
    public static PageMetadataDetails from(Page<?> page) {
        return new PageMetadataDetails(
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast()
        );
    }
}
