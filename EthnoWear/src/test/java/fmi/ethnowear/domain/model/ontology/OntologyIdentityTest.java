package fmi.ethnowear.domain.model.ontology;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OntologyIdentityTest {

    @Test
    void recognizesCompleteIdentity() {
        OntologyIdentity identity = new OntologyIdentity(
                "urn:ethnowear#ShoplukEmbroidery",
                "ShoplukEmbroidery"
        );

        assertTrue(identity.isComplete());
        assertFalse(identity.isAbsent());
        assertFalse(identity.isIncomplete());
    }

    @Test
    void recognizesAbsentIdentity() {
        OntologyIdentity identity = new OntologyIdentity(null, " ");

        assertFalse(identity.isComplete());
        assertTrue(identity.isAbsent());
        assertFalse(identity.isIncomplete());
    }

    @Test
    void recognizesIncompleteIdentity() {
        OntologyIdentity identity = new OntologyIdentity(
                "urn:ethnowear#ShoplukEmbroidery",
                null
        );

        assertFalse(identity.isComplete());
        assertFalse(identity.isAbsent());
        assertTrue(identity.isIncomplete());
    }
}
