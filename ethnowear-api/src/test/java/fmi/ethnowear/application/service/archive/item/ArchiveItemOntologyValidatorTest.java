package fmi.ethnowear.application.service.archive.item;

import fmi.ethnowear.application.dto.archive.item.ArchiveItemWriteDto;
import fmi.ethnowear.domain.model.archive.ArchiveType;
import fmi.ethnowear.domain.model.archive.TrustedLevel;
import fmi.ethnowear.application.port.ontology.EmbroideryOntologyClient;
import fmi.ethnowear.domain.model.ontology.OntologyResource;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static fmi.ethnowear.support.RepositoryTestProxies.proxy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ArchiveItemOntologyValidatorTest {

    private static final String SHOPLUK_IRI = "urn:ethnowear#ShoplukEmbroidery";
    private static final String REGION_IRI = "urn:ethnowear#ShoplukRegion";
    private static final String MOTIF_IRI = "urn:ethnowear#ShoplukMotif";

    @Test
    void acceptsMatchingRegionalEmbroideryIdentity() {
        ArchiveItemOntologyValidator validator = validator();

        assertDoesNotThrow(() -> validator.validateClassifications(
                input(SHOPLUK_IRI, "ShoplukEmbroidery")
        ));
    }

    @Test
    void rejectsIncompleteRegionalEmbroideryIdentity() {
        ArchiveItemOntologyValidator validator = validator();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> validator.validateClassifications(input(null, "ShoplukEmbroidery"))
        );

        assertEquals(
                "Regional embroidery IRI and local name must be provided together",
                exception.getMessage()
        );
    }

    @Test
    void rejectsMismatchedRegionalEmbroideryIri() {
        ArchiveItemOntologyValidator validator = validator();

        assertThrows(
                IllegalArgumentException.class,
                () -> validator.validateClassifications(
                        input("urn:ethnowear#OtherEmbroidery", "ShoplukEmbroidery")
                )
        );
    }

    @Test
    void acceptsRegionalMotifBelongingToSelectedRegion() {
        ArchiveItemOntologyValidator validator = validator();

        assertDoesNotThrow(() -> validator.validateClassifications(
                new ArchiveItemWriteDto(
                        1L, null, null, null, "Archive item", null, null,
                        ArchiveType.MOTIF_EXAMPLE, null, null, null,
                        TrustedLevel.VERIFIED, REGION_IRI, "ShoplukRegion",
                        null, null, MOTIF_IRI, "ShoplukMotif"
                )
        ));
    }

    private ArchiveItemOntologyValidator validator() {
        EmbroideryOntologyClient ontology = proxy(
                EmbroideryOntologyClient.class,
                (ignored, method, arguments) -> {
                    if (method.getName().equals("listRegionalEmbroideryTypes"))
                        return List.of(new OntologyResource(
                                SHOPLUK_IRI,
                                "ShoplukEmbroidery",
                                "Shopluk embroidery"
                        ));

                    if (method.getName().equals("listRegions"))
                        return List.of(new OntologyResource(
                                REGION_IRI,
                                "ShoplukRegion",
                                "Shopluk"
                        ));

                    if (method.getName().equals("findRegionForRegionalEmbroidery"))
                        return Optional.of(new OntologyResource(
                                REGION_IRI,
                                "ShoplukRegion",
                                "Shopluk"
                        ));

                    if (method.getName().equals("listRegionalMotifTypes"))
                        return List.of(new OntologyResource(
                                MOTIF_IRI,
                                "ShoplukMotif",
                                "Shopluk motif"
                        ));

                    if (method.getName().equals("findRegionForRegionalMotif"))
                        return Optional.of(new OntologyResource(
                                REGION_IRI,
                                "ShoplukRegion",
                                "Shopluk"
                        ));

                    throw new AssertionError("Unexpected ontology call: " + method.getName());
                }
        );

        return new ArchiveItemOntologyValidator(ontology);
    }

    private ArchiveItemWriteDto input(String embroideryIri, String embroideryLocalName) {
        return new ArchiveItemWriteDto(
                1L,
                null,
                null,
                null,
                "Archive item",
                null,
                null,
                ArchiveType.EMBROIDERY_SAMPLE,
                null,
                null,
                null,
                TrustedLevel.VERIFIED,
                REGION_IRI,
                "ShoplukRegion",
                embroideryIri,
                embroideryLocalName,
                null,
                null
        );
    }
}
