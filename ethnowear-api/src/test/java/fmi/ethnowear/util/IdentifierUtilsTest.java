package fmi.ethnowear.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IdentifierUtilsTest {

    @Test
    void requireIdReturnsValidId() {
        assertEquals(42L, IdentifierUtils.requireId(42L, "Document"));
    }

    @Test
    void requireIdRejectsNullId() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> IdentifierUtils.requireId(null, "Document")
        );

        assertEquals("Document id is required", exception.getMessage());
    }

    @Test
    void requireIdRejectsZeroId() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> IdentifierUtils.requireId(0L, "Document")
        );

        assertEquals("Document id must be positive", exception.getMessage());
    }

    @Test
    void requireIdRejectsNegativeId() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> IdentifierUtils.requireId(-1L, "Document")
        );

        assertEquals("Document id must be positive", exception.getMessage());
    }
}
