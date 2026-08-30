package fmi.ethnowear.application.service.catalogue;

import fmi.ethnowear.application.dto.catalogue.ConceptCatalogQueryDto;
import fmi.ethnowear.application.dto.catalogue.ConceptCatalogResultDetails;
import fmi.ethnowear.application.dto.catalogue.ConceptEvidenceSummaryDetails;
import fmi.ethnowear.application.service.catalogue.mapper.ConceptCatalogMatcher;
import fmi.ethnowear.application.service.catalogue.mapper.EntityCardMapper;
import fmi.ethnowear.application.service.catalogue.mapper.OntologyReferenceMapper;
import fmi.ethnowear.domain.model.ontology.FeatureType;
import fmi.ethnowear.domain.model.catalogue.FilterCombinationMode;
import fmi.ethnowear.infrastructure.ontology.jena.embroidery.EmbroideryOntology;
import fmi.ethnowear.infrastructure.ontology.jena.JenaOntologyStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
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
                : Path.of("ethnowear-api/Ontology/EthnoWear.owx");
        Files.copy(source, ontologyCopy);

        EmbroideryOntology ontology = new EmbroideryOntology(
                new JenaOntologyStore(ontologyCopy, NAMESPACE)
        );
        OntologyReferenceMapper referenceMapper = new OntologyReferenceMapper();
        OntologyCategoryReader categoryReader = new OntologyCategoryReader(
                ontology,
                referenceMapper
        );
        OntologyEntityDetailReader reader = new OntologyEntityDetailReader(
                ontology,
                referenceMapper,
                categoryReader
        );
        ConceptCatalogMatcher matcher = new ConceptCatalogMatcher(categoryReader);
        ConceptCatalogFacetService facetService = new ConceptCatalogFacetService(
                matcher,
                categoryReader
        );
        EntityCardMapper mapper = new EntityCardMapper();
        ConceptCardEvidenceService evidenceService = new ConceptCardEvidenceService(null, null, null) {
            @Override
            public Map<String, ConceptEvidenceSummaryDetails> summarize(
                    FeatureType entityType,
                    java.util.Collection<String> ontologyIris
            ) {
                return Map.of();
            }
        };
        service = new ConceptCatalogService(reader, matcher, facetService, mapper, evidenceService);
    }

    @Test
    void searchesCaseInsensitively() {
        ConceptCatalogResultDetails result = service.search(
                query("SOFIA", Map.of()),
                PageRequest.of(0, 12)
        );

        assertTrue(result.items().stream()
                .anyMatch(entity -> entity.localName().equals("SofiaEmbroidery")));
        assertFalse(result.facets().isEmpty());
    }

    @Test
    void filtersByCategoryOfRelatedRegion() {
        ConceptCatalogResultDetails result = service.search(
                query(null, Map.of(
                        FeatureType.REGION,
                        List.of("WesternRegionGroup")
                )),
                PageRequest.of(0, 100)
        );

        assertTrue(result.items().stream()
                .anyMatch(entity -> entity.localName().equals("SofiaEmbroidery")));
        assertFalse(result.items().stream()
                .anyMatch(entity -> entity.localName().equals("ElhovoEmbroidery")));
        assertTrue(result.facets().stream()
                .filter(group -> group.entityType() == FeatureType.REGION)
                .flatMap(group -> group.values().stream())
                .anyMatch(value -> value.localName().equals("WesternRegionGroup")
                        && value.selected()
                        && value.count() > 0));
    }

    @Test
    void sortsByLocalNameAndPaginates() {
        ConceptCatalogResultDetails result = service.search(
                query(null, Map.of()),
                PageRequest.of(0, 2, Sort.by(Sort.Direction.DESC, "localName"))
        );

        assertEquals(2, result.page().size());
        assertEquals(2, result.items().size());
        assertTrue(result.page().totalElements() > result.items().size());
        assertTrue(result.items().get(0).localName()
                .compareToIgnoreCase(result.items().get(1).localName()) >= 0);
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
