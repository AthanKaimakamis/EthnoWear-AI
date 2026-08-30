package fmi.ethnowear.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RowVersionUtilsTest {

    @Test
    void acceptsRawAndQuotedMatchingTokens() {
        byte[] rowVersion = {1, 2, 3, 4, 5, 6, 7, 8};
        String token = RowVersionUtils.token(rowVersion);

        assertDoesNotThrow(() -> RowVersionUtils.requireMatch(rowVersion, token));
        assertDoesNotThrow(() -> RowVersionUtils.requireMatch(
                rowVersion,
                "\"" + token + "\""
        ));
    }

    @Test
    void rejectsMissingInvalidAndStaleTokens() {
        byte[] rowVersion = {1, 2, 3, 4, 5, 6, 7, 8};

        assertThrows(
                IllegalArgumentException.class,
                () -> RowVersionUtils.requireMatch(rowVersion, null)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> RowVersionUtils.requireMatch(rowVersion, "not-base64!")
        );
        assertThrows(
                RowVersionUtils.StaleRowVersionException.class,
                () -> RowVersionUtils.requireMatch(rowVersion, "AQ")
        );
    }
}
