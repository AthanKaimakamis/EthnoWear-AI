package fmi.ethnowear.application.service.archive.knowledge;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KnowledgeContentHasherTest {

    private final KnowledgeContentHasher hasher = new KnowledgeContentHasher();

    @Test
    void hashesExactUtf8ContentAsLowercaseSha256() {
        assertEquals(
                "ca7410c08dcf762308545ee499bee2400735ed1537c097fa558cf2e086c2dfa7",
                hasher.hash("българска шевица")
        );
    }

    @Test
    void doesNotTrimOrNormalizeContent() {
        assertEquals(
                "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
                hasher.hash("hello")
        );
        assertEquals(
                "61c2ba73ffe637de9d0be2dbb4d466d6d44acdd5d689730c5cfb62e5dd2fceb0",
                hasher.hash(" hello ")
        );
    }

    @Test
    void rejectsNullContent() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> hasher.hash(null)
        );

        assertEquals("Knowledge content is required", exception.getMessage());
    }
}
