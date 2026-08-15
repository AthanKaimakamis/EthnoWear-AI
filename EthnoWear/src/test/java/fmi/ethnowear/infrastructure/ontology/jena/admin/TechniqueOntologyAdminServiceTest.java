package fmi.ethnowear.infrastructure.ontology.jena.admin;

import fmi.ethnowear.application.exception.InvalidTechniqueException;
import fmi.ethnowear.application.exception.TechniqueInUseException;
import fmi.ethnowear.domain.constant.ontology.OntologyTerms;
import fmi.ethnowear.application.dto.ontology.admin.TechniqueCreateCommand;
import fmi.ethnowear.application.dto.ontology.admin.TechniqueUpdateCommand;
import fmi.ethnowear.application.dto.ontology.admin.TechniqueDetails;
import fmi.ethnowear.infrastructure.ontology.jena.JenaOntologyStore;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TechniqueOntologyAdminServiceTest {

    private static final String NAMESPACE =
            "http://www.semanticweb.org/athan/ontologies/2026/4/ethno-wear#";

    @TempDir
    Path temporaryDirectory;

    private JenaOntologyStore store;
    private TechniqueOntologyAdminService service;

    @BeforeEach
    void setUp() throws Exception {
        Path ontologyCopy = temporaryDirectory.resolve("EthnoWear.owx");
        Path source = Files.exists(Path.of("Ontology/EthnoWear.owx"))
                ? Path.of("Ontology/EthnoWear.owx")
                : Path.of("EthnoWear/Ontology/EthnoWear.owx");
        Files.copy(source, ontologyCopy);

        store = new JenaOntologyStore(ontologyCopy, NAMESPACE);
        service = new TechniqueOntologyAdminService(
                store,
                new OntologyAdminModelSupport(store)
        );
    }

    @Test
    void createsAndReloadsTechnique() {
        TechniqueDetails created = service.create(command("TestChainStitch"));

        assertEquals("TestChainStitch", created.localName());
        assertEquals(Set.of(OntologyTerms.Classes.CHAIN_TECHNIQUE), created.typeLocalNames());
        assertEquals("тестов синджирен бод", created.labelBg());
        assertEquals(Set.of("SofiaRegion"), created.characteristicRegionLocalNames());

        store.reload();
        assertEquals(created, service.get("TestChainStitch").orElseThrow());
    }

    @Test
    void replacesTechniqueValuesOnUpdate() {
        service.create(command("UpdatedStitch"));

        TechniqueDetails updated = service.update(
                "UpdatedStitch",
                new TechniqueUpdateCommand(
                        Set.of(OntologyTerms.Classes.CROSS_TECHNIQUE),
                        "обновен кръстат бод",
                        "updated cross stitch",
                        List.of(),
                        List.of("cross technique"),
                        null,
                        "Updated comment.",
                        Set.of()
                )
        );

        assertEquals(Set.of(OntologyTerms.Classes.CROSS_TECHNIQUE), updated.typeLocalNames());
        assertEquals("updated cross stitch", updated.labelEn());
        assertTrue(updated.characteristicRegionLocalNames().isEmpty());
        store.reload();
        assertEquals(updated, service.get("UpdatedStitch").orElseThrow());
    }

    @Test
    void addsAndRemovesCharacteristicRegion() {
        service.create(commandWithoutRegions("RegionalStitch"));

        TechniqueDetails assigned = service.addCharacteristicRegion("RegionalStitch", "SofiaRegion");
        assertEquals(Set.of("SofiaRegion"), assigned.characteristicRegionLocalNames());

        TechniqueDetails removed = service.removeCharacteristicRegion("RegionalStitch", "SofiaRegion");
        assertTrue(removed.characteristicRegionLocalNames().isEmpty());
    }

    @Test
    void rejectsDeletionWhenTechniqueIsReferenced() {
        service.create(commandWithoutRegions("ReferencedStitch"));
        store.write(model -> {
            Resource region = model.getResource(store.uri("SofiaRegion"));
            Resource technique = model.getResource(store.uri("ReferencedStitch"));
            Property property = model.getProperty(store.uri(
                    OntologyTerms.ObjectProperties.REGION_USES_TECHNIQUE
            ));
            region.addProperty(property, technique);
            return null;
        });

        assertThrows(TechniqueInUseException.class, () -> service.delete("ReferencedStitch"));
        assertTrue(service.get("ReferencedStitch").isPresent());
    }

    @Test
    void rollsBackInvalidUpdate() {
        TechniqueDetails original = service.create(command("RollbackStitch"));

        assertThrows(
                InvalidTechniqueException.class,
                () -> service.update(
                        "RollbackStitch",
                        new TechniqueUpdateCommand(
                                Set.of(OntologyTerms.Classes.OPENWORK_TECHNIQUE),
                                "променен бод",
                                null,
                                List.of(),
                                List.of(),
                                null,
                                null,
                                Set.of("MissingRegion")
                        )
                )
        );

        assertEquals(original, service.get("RollbackStitch").orElseThrow());
    }

    private TechniqueCreateCommand command(String localName) {
        return new TechniqueCreateCommand(
                localName,
                Set.of(OntologyTerms.Classes.CHAIN_TECHNIQUE),
                "тестов синджирен бод",
                "test chain stitch",
                List.of("синджирен тест"),
                List.of("chain technique test"),
                "Тестов коментар.",
                "Test comment.",
                Set.of("SofiaRegion")
        );
    }

    private TechniqueCreateCommand commandWithoutRegions(String localName) {
        return new TechniqueCreateCommand(
                localName,
                Set.of(OntologyTerms.Classes.CONTOUR_TECHNIQUE),
                "временен бод",
                "temporary stitch",
                List.of(),
                List.of(),
                null,
                null,
                Set.of()
        );
    }
}
