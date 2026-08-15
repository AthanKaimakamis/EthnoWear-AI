package fmi.ethnowear.application.service.catalogue;

import fmi.ethnowear.api.dto.catalogue.CategoryLinkDetails;
import fmi.ethnowear.application.enums.FeatureType;
import fmi.ethnowear.ontology.embroidery.EmbroideryOntology;
import fmi.ethnowear.ontology.jena.JenaOntologyStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class OntologyCategoryReaderTest {

    private static final String NAMESPACE =
            "http://www.semanticweb.org/athan/ontologies/2026/4/ethno-wear#";

    @TempDir
    Path temporaryDirectory;

    private OntologyCategoryReader reader;

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
        reader = new OntologyCategoryReader(
                ontology,
                new OntologyReferenceMapper()
        );
    }

    @Test
    void returnsDistinctLocalizedCategoryLinksForSeveralEntities() {
        List<CategoryLinkDetails> result = reader.findCategoryLinks(
                FeatureType.TECHNIQUE,
                List.of("ReturnStitch", "ReturnStitch"),
                "bg"
        );

        assertFalse(result.isEmpty());
        assertEquals(result.stream().map(CategoryLinkDetails::localName).distinct().count(), result.size());
        result.forEach(category -> {
            assertEquals(FeatureType.TECHNIQUE, category.targetEntityType());
            assertFalse(category.label().isBlank());
        });
    }
}
