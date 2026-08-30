package fmi.ethnowear.domain.model.ontology;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OntologyLanguageTest {

    @Test
    void parsesSupportedTagsAndDefaultsBlankToBulgarian() {
        assertEquals(OntologyLanguage.BG, OntologyLanguage.fromTag(null));
        assertEquals(OntologyLanguage.BG, OntologyLanguage.fromTag(" "));
        assertEquals(OntologyLanguage.EN, OntologyLanguage.fromTag(" EN "));
    }

    @Test
    void rejectsUnsupportedLanguage() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> OntologyLanguage.fromTag("de")
        );

        assertEquals("Unsupported language: de", exception.getMessage());
    }
}
