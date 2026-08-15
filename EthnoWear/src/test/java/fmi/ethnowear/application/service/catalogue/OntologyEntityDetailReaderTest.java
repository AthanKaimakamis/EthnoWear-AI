package fmi.ethnowear.application.service.catalogue;

import fmi.ethnowear.application.dto.catalogue.EntityOntologyDetails;
import fmi.ethnowear.domain.model.ontology.FeatureType;
import fmi.ethnowear.domain.constant.ontology.OntologyTerms;
import fmi.ethnowear.infrastructure.ontology.jena.embroidery.EmbroideryOntology;
import fmi.ethnowear.infrastructure.ontology.jena.JenaOntologyStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OntologyEntityDetailReaderTest {

    private static final String NAMESPACE =
            "http://www.semanticweb.org/athan/ontologies/2026/4/ethno-wear#";

    @TempDir
    Path temporaryDirectory;

    private OntologyEntityDetailReader reader;

    @BeforeEach
    void setUp() throws Exception {
        Path ontologyCopy = temporaryDirectory.resolve("EthnoWear.owx");
        Path source = Files.exists(Path.of("Ontology/EthnoWear.owx"))
                ? Path.of("Ontology/EthnoWear.owx")
                : Path.of("EthnoWear/Ontology/EthnoWear.owx");
        Files.copy(source, ontologyCopy);

        EmbroideryOntology ontology = new EmbroideryOntology(
                new JenaOntologyStore(ontologyCopy, NAMESPACE)
        );
        OntologyReferenceMapper referenceMapper = new OntologyReferenceMapper();
        OntologyCategoryReader categoryReader = new OntologyCategoryReader(
                ontology,
                referenceMapper
        );
        reader = new OntologyEntityDetailReader(
                ontology,
                referenceMapper,
                categoryReader
        );
    }

    @Test
    void returnsLocalizedRegionWithRegionalEmbroideryRelation() {
        EntityOntologyDetails details = reader.find(
                FeatureType.REGION,
                "SofiaRegion",
                "bg"
        );

        assertEquals("SofiaRegion", details.localName());
        assertEquals("bg", details.language());
        assertTrue(details.relatedEntities()
                .get(FeatureType.REGIONAL_EMBROIDERY)
                .stream()
                .anyMatch(reference ->
                        reference.entityType() == FeatureType.REGIONAL_EMBROIDERY
                                && reference.localName().equals("SofiaEmbroidery")
                ));
    }

    @Test
    void excludesRootOrnamentClassFromCategoriesAndFindsInverseRegion() {
        EntityOntologyDetails details = reader.find(
                FeatureType.ORNAMENT,
                "TreeOfLifeOrnament",
                "en"
        );

        assertFalse(details.categories().stream()
                .anyMatch(reference -> reference.localName().equals(OntologyTerms.Classes.ORNAMENT)));
        assertTrue(details.categories().stream()
                .anyMatch(reference ->
                        reference.targetEntityType() == FeatureType.ORNAMENT
                                && reference.localName().equals(OntologyTerms.Classes.PLANT_ORNAMENT)
                ));
        assertTrue(details.relatedEntities()
                .get(FeatureType.REGION)
                .stream()
                .anyMatch(reference ->
                        reference.entityType() == FeatureType.REGION
                                && reference.localName().equals("ElhovoRegion")
                ));
    }
}
