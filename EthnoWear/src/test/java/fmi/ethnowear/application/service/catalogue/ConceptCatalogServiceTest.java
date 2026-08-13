package fmi.ethnowear.application.service.catalogue;

import fmi.ethnowear.api.dto.catalogue.ConceptCatalogQueryDto;
import fmi.ethnowear.api.dto.catalogue.EntityOntologyDetails;
import fmi.ethnowear.application.enums.FeatureType;
import fmi.ethnowear.application.enums.FilterCombinationMode;
import fmi.ethnowear.ontology.embroidery.EmbroideryOntology;
import fmi.ethnowear.ontology.jena.JenaOntologyStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConceptCatalogServiceTest {

    private static final String NAMESPACE =
            "http://www.semanticweb.org/athan/ontologies/2026/4/ethno-wear#";

    @TempDir
    Path temporaryDirectory;

    private ConceptCatalogService service;

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
        OntologyEntityDetailReader reader = new OntologyEntityDetailReader(
                ontology,
                new OntologyReferenceMapper()
        );
        service = new ConceptCatalogService(reader, ontology);
    }

    @Test
    void searchesCaseInsensitively() {
        Page<EntityOntologyDetails> result = service.search(
                query("SOFIA", Map.of()),
                PageRequest.of(0, 12)
        );

        assertTrue(result.stream()
                .anyMatch(entity -> entity.localName().equals("SofiaEmbroidery")));
    }

    @Test
    void filtersByCategoryOfRelatedRegion() {
        Page<EntityOntologyDetails> result = service.search(
                query(null, Map.of(
                        FeatureType.REGION,
                        List.of("WesternRegionGroup")
                )),
                PageRequest.of(0, 100)
        );

        assertTrue(result.stream()
                .anyMatch(entity -> entity.localName().equals("SofiaEmbroidery")));
        assertFalse(result.stream()
                .anyMatch(entity -> entity.localName().equals("ElhovoEmbroidery")));
    }

    @Test
    void sortsByLocalNameAndPaginates() {
        Page<EntityOntologyDetails> result = service.search(
                query(null, Map.of()),
                PageRequest.of(0, 2, Sort.by(Sort.Direction.DESC, "localName"))
        );

        assertEquals(2, result.getNumberOfElements());
        assertTrue(result.getTotalElements() > result.getNumberOfElements());
        assertTrue(result.getContent().get(0).localName()
                .compareToIgnoreCase(result.getContent().get(1).localName()) >= 0);
    }

    private ConceptCatalogQueryDto query(
            String searchText,
            Map<FeatureType, List<String>> relatedCategories
    ) {
        return new ConceptCatalogQueryDto(
                FeatureType.REGIONAL_EMBROIDERY,
                "en",
                searchText,
                List.of(),
                Map.of(),
                relatedCategories,
                FilterCombinationMode.AND
        );
    }
}
