package fmi.ethnowear.ontology.admin;

import fmi.ethnowear.ontology.OntologyTerms;
import fmi.ethnowear.ontology.jena.JenaOntologyStore;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrnamentOntologyAdminServiceTest {

    private static final String NAMESPACE =
            "http://www.semanticweb.org/athan/ontologies/2026/4/ethno-wear#";

    @TempDir
    Path temporaryDirectory;

    private JenaOntologyStore store;
    private OrnamentOntologyAdminService service;

    @BeforeEach
    void setUp() throws Exception {
        Path ontologyCopy = temporaryDirectory.resolve("EthnoWear.owx");
        Path source = Files.exists(Path.of("Ontology/EthnoWear.owx"))
                ? Path.of("Ontology/EthnoWear.owx")
                : Path.of("EthnoWear/Ontology/EthnoWear.owx");
        Files.copy(source, ontologyCopy);

        store = new JenaOntologyStore(ontologyCopy, NAMESPACE);
        service = new OrnamentOntologyAdminService(store);
    }

    @Test
    void createsAndReloadsLocalizedOrnament() {
        OrnamentDetails created = service.create(command("TestFlowerOrnament"));

        assertEquals("TestFlowerOrnament", created.localName());
        assertEquals(Set.of(
                OntologyTerms.Classes.PLANT_ORNAMENT,
                OntologyTerms.Classes.SYMBOLIC_ORNAMENT
        ), created.typeLocalNames());
        assertEquals("тестово цвете", created.labelBg());
        assertEquals("test flower", created.labelEn());
        assertEquals(Set.of("SofiaRegion"), created.characteristicRegionLocalNames());

        store.reload();

        OrnamentDetails reloaded = service.get("TestFlowerOrnament").orElseThrow();
        assertEquals(created.localName(), reloaded.localName());
        assertEquals(created.typeLocalNames(), reloaded.typeLocalNames());
        assertEquals(created.characteristicRegionLocalNames(), reloaded.characteristicRegionLocalNames());
    }

    @Test
    void rejectsDuplicateLocalName() {
        service.create(command("TestFlowerOrnament"));

        assertThrows(
                OrnamentAlreadyExistsException.class,
                () -> service.create(command("TestFlowerOrnament"))
        );
    }

    @Test
    void deletesUnreferencedOrnament() {
        service.create(commandWithoutRegions("TemporaryOrnament"));

        service.delete("TemporaryOrnament");

        assertTrue(service.get("TemporaryOrnament").isEmpty());
        store.reload();
        assertTrue(service.get("TemporaryOrnament").isEmpty());
    }

    @Test
    void rejectsDeletionWhenOrnamentHasIncomingReference() {
        service.create(commandWithoutRegions("ReferencedOrnament"));
        store.write(model -> {
            Resource region = model.getResource(store.uri("SofiaRegion"));
            Resource ornament = model.getResource(store.uri("ReferencedOrnament"));
            Property property = model.getProperty(store.uri(
                    OntologyTerms.ObjectProperties.REGION_USES_ORNAMENT
            ));
            region.addProperty(property, ornament);
            return null;
        });

        OrnamentInUseException exception = assertThrows(
                OrnamentInUseException.class,
                () -> service.delete("ReferencedOrnament")
        );

        assertFalse(exception.getReferences().isEmpty());
        assertEquals("SofiaRegion", exception.getReferences().getFirst().subjectLocalName());
        assertTrue(service.get("ReferencedOrnament").isPresent());
    }

    @Test
    void replacesManagedValuesWhenUpdatingOrnament() {
        service.create(command("UpdatedOrnament"));

        OrnamentDetails updated = service.update(
                "UpdatedOrnament",
                new OrnamentUpdateCommand(
                        Set.of(OntologyTerms.Classes.ANIMAL_ORNAMENT),
                        "обновен орнамент",
                        "updated ornament",
                        List.of("обновен"),
                        List.of(),
                        null,
                        "Updated comment.",
                        Set.of()
                )
        );

        assertEquals(Set.of(OntologyTerms.Classes.ANIMAL_ORNAMENT), updated.typeLocalNames());
        assertEquals("обновен орнамент", updated.labelBg());
        assertEquals(List.of("обновен"), updated.altLabelsBg());
        assertTrue(updated.altLabelsEn().isEmpty());
        assertEquals("Updated comment.", updated.commentEn());
        assertTrue(updated.characteristicRegionLocalNames().isEmpty());

        store.reload();
        assertEquals(updated, service.get("UpdatedOrnament").orElseThrow());
    }

    @Test
    void addsAndRemovesCharacteristicRegion() {
        service.create(commandWithoutRegions("RegionalOrnament"));

        OrnamentDetails assigned = service.addCharacteristicRegion("RegionalOrnament", "SofiaRegion");
        assertEquals(Set.of("SofiaRegion"), assigned.characteristicRegionLocalNames());

        OrnamentDetails removed = service.removeCharacteristicRegion("RegionalOrnament", "SofiaRegion");
        assertTrue(removed.characteristicRegionLocalNames().isEmpty());
    }

    @Test
    void updateRollsBackWhenRegionDoesNotExist() {
        OrnamentDetails original = service.create(command("RollbackOrnament"));

        assertThrows(
                InvalidOrnamentException.class,
                () -> service.update(
                        "RollbackOrnament",
                        new OrnamentUpdateCommand(
                                Set.of(OntologyTerms.Classes.HUMAN_ORNAMENT),
                                "променен",
                                null,
                                List.of(),
                                List.of(),
                                null,
                                null,
                                Set.of("MissingRegion")
                        )
                )
        );

        assertEquals(original, service.get("RollbackOrnament").orElseThrow());
    }

    private OrnamentCreateCommand command(String localName) {
        return new OrnamentCreateCommand(
                localName,
                Set.of(
                        OntologyTerms.Classes.PLANT_ORNAMENT,
                        OntologyTerms.Classes.SYMBOLIC_ORNAMENT
                ),
                "тестово цвете",
                "test flower",
                List.of("цвете тест"),
                List.of("flower test"),
                "Тестов коментар.",
                "Test comment.",
                Set.of("SofiaRegion")
        );
    }

    private OrnamentCreateCommand commandWithoutRegions(String localName) {
        return new OrnamentCreateCommand(
                localName,
                Set.of(OntologyTerms.Classes.GEOMETRIC_ORNAMENT),
                "временен орнамент",
                "temporary ornament",
                List.of(),
                List.of(),
                null,
                null,
                Set.of()
        );
    }
}
