package fmi.ethnowear.util;

import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Locale;

public final class TextUtils {

    private TextUtils() {
    }

    public static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public static boolean isNotBlank(String value) {
        return !isBlank(value);
    }

    public static String defaultIfBlank(String value, String defaultValue) {
        return isBlank(value) ? defaultValue : value;
    }

    public static @NonNull String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    public static List<String> values(List<String> values) {
        if(values == null)
            return List.of();

        return values.stream()
                .filter(value -> !isBlank(value))
                .map(String::trim)
                .distinct()
                .toList();
    }
}
