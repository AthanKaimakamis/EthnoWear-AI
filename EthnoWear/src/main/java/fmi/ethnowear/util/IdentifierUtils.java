package fmi.ethnowear.util;

import org.jspecify.annotations.NonNull;

public final class IdentifierUtils {

    private IdentifierUtils() {
    }

    public static @NonNull Long requireId(Long id, String resourceName) {
        if (id == null)
            throw new IllegalArgumentException(resourceName + " id is required");

        if (id <= 0)
            throw new IllegalArgumentException(resourceName + " id must be positive");

        return id;
    }
}
