package fmi.ethnowear.util;

import java.util.Arrays;
import java.util.Base64;

public final class RowVersionUtils {

    private RowVersionUtils() {
    }

    public static String token(byte[] rowVersion) {
        return rowVersion == null
                ? null
                : Base64.getUrlEncoder()
                        .withoutPadding()
                        .encodeToString(rowVersion);
    }

    public static void requireMatch(byte[] rowVersion, String token) {
        if (token == null || token.isBlank())
            throw new IllegalArgumentException("If-Match version token is required");

        String normalized = token.trim();

        if (normalized.startsWith("\"") && normalized.endsWith("\""))
            normalized = normalized.substring(1, normalized.length() - 1);

        byte[] expected;

        try {
            expected = Base64.getUrlDecoder().decode(normalized);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("If-Match version token is invalid", ex);
        }

        if (rowVersion == null || !Arrays.equals(rowVersion, expected))
            throw new StaleRowVersionException();
    }

    public static final class StaleRowVersionException
            extends RuntimeException {

        public StaleRowVersionException() {
            super("The resource changed after it was loaded");
        }
    }
}
