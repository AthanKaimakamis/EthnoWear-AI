package fmi.ethnowear.util;

import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class TextUtils {

    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^\\p{L}\\p{N}]+");

    private static final Pattern MULTIPLE_WHITESPACE = Pattern.compile("\\s+");

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
        if (values == null)
            return List.of();

        return values.stream()
                .filter(value -> !isBlank(value))
                .map(String::trim)
                .distinct()
                .toList();
    }

    public static @NonNull String normalizeSearchText(String value) {
        if (value == null)
            return "";

        String normalized = NON_ALPHANUMERIC
                .matcher(value.toLowerCase(Locale.ROOT))
                .replaceAll(" ")
                .trim();

        return MULTIPLE_WHITESPACE
                .matcher(normalized)
                .replaceAll(" ");
    }
}
