package fmi.ethnowear.util;

import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

public final class PageableUtils {

    private PageableUtils() {
    }

    @Contract("null, _, _ -> fail")
    public static @NonNull Pageable bounded(
            Pageable pageable,
            int maximumPageSize,
            String context
    ) {
        validate(pageable, maximumPageSize, context);

        return PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                pageable.getSort()
        );
    }

    @Contract("null, _, _ -> fail")
    public static @NonNull Pageable boundedUnsorted(
            Pageable pageable,
            int maximumPageSize,
            String context
    ) {
        validate(pageable, maximumPageSize, context);

        return PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize()
        );
    }

    private static void validate(
            Pageable pageable,
            int maximumPageSize,
            String context
    ) {
        if (pageable == null || pageable.isUnpaged())
            throw new IllegalArgumentException(
                    context + " pageable is required"
            );

        if (maximumPageSize <= 0)
            throw new IllegalArgumentException(
                    "Maximum page size must be positive"
            );

        if (pageable.getPageSize() > maximumPageSize)
            throw new IllegalArgumentException(
                    context + " page size cannot exceed " + maximumPageSize
            );
    }
}
