package fmi.ethnowear.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import fmi.ethnowear.api.controller.catalogue.CatalogueController;
import fmi.ethnowear.api.converter.StringToEnumConverterFactory;
import fmi.ethnowear.application.dto.catalogue.ConceptCatalogQueryDto;
import fmi.ethnowear.application.dto.catalogue.ConceptCatalogResultDetails;
import fmi.ethnowear.application.dto.catalogue.EntityDetailDetails;
import fmi.ethnowear.application.dto.catalogue.EntityCardDetails;
import fmi.ethnowear.application.dto.catalogue.EntityOntologyDetails;
import fmi.ethnowear.application.dto.catalogue.PageMetadataDetails;
import fmi.ethnowear.domain.model.ontology.FeatureType;
import fmi.ethnowear.domain.model.catalogue.FilterCombinationMode;
import fmi.ethnowear.application.service.catalogue.ConceptCatalogService;
import fmi.ethnowear.application.service.catalogue.EntityDetailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.format.support.DefaultFormattingConversionService;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CatalogueControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private StubConceptCatalogService catalogService;
    private StubEntityDetailService entityDetailService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        catalogService = new StubConceptCatalogService();
        entityDetailService = new StubEntityDetailService();

        DefaultFormattingConversionService conversionService = new DefaultFormattingConversionService();
        conversionService.addConverterFactory(new StringToEnumConverterFactory());

        mockMvc = MockMvcBuilders
                .standaloneSetup(new CatalogueController(catalogService, entityDetailService))
                .setConversionService(conversionService)
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
    }

    @Test
    void forwardsSearchQueryAndPagination() throws Exception {
        ConceptCatalogQueryDto query = new ConceptCatalogQueryDto(
                FeatureType.TECHNIQUE,
                "bg",
                "бод",
                List.of("ContourTechnique"),
                Map.of(FeatureType.REGION, List.of("Shopluk")),
                Map.of(),
                FilterCombinationMode.AND
        );
        catalogService.result = new ConceptCatalogResultDetails(
                List.of(cardDetails(FeatureType.TECHNIQUE, "ReturnStitch")),
                new PageMetadataDetails(0, 1, 1, 1, true, true),
                List.of()
        );

        mockMvc.perform(post("/api/catalogue/search")
                        .queryParam("page", "2")
                        .queryParam("size", "5")
                        .queryParam("sort", "localName,desc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(query)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].localName").value("ReturnStitch"))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.facets").isEmpty());

        assertEquals(query, catalogService.query);
        assertEquals(2, catalogService.pageable.getPageNumber());
        assertEquals(5, catalogService.pageable.getPageSize());
        assertEquals("DESC", catalogService.pageable.getSort().getOrderFor("localName").getDirection().name());
    }

    @Test
    void convertsEntityTypeAliasAndAppliesDetailDefaults() throws Exception {
        EntityOntologyDetails ontology = ontologyDetails(FeatureType.REGIONAL_EMBROIDERY, "ShoplukEmbroidery");
        entityDetailService.result = new EntityDetailDetails(
                ontology,
                null,
                new PageImpl<>(List.of(), PageRequest.of(0, 12), 0)
        );

        mockMvc.perform(get("/api/catalogue/regional-embroideries/ShoplukEmbroidery"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ontology.entityType").value("REGIONAL_EMBROIDERY"))
                .andExpect(jsonPath("$.ontology.localName").value("ShoplukEmbroidery"));

        assertEquals(FeatureType.REGIONAL_EMBROIDERY, entityDetailService.entityType);
        assertEquals("ShoplukEmbroidery", entityDetailService.localName);
        assertEquals("bg", entityDetailService.language);
        assertEquals(0, entityDetailService.pageable.getPageNumber());
        assertEquals(12, entityDetailService.pageable.getPageSize());
        assertEquals("DESC", entityDetailService.pageable.getSort().getOrderFor("id").getDirection().name());
    }

    @Test
    void forwardsDetailPaginationAndLanguage() throws Exception {
        EntityOntologyDetails ontology = ontologyDetails(FeatureType.TECHNIQUE, "ReturnStitch");
        entityDetailService.result = new EntityDetailDetails(
                ontology,
                null,
                new PageImpl<>(List.of(), PageRequest.of(1, 3), 0)
        );

        mockMvc.perform(get("/api/catalogue/techniques/ReturnStitch")
                        .queryParam("language", "en")
                        .queryParam("page", "1")
                        .queryParam("size", "3")
                        .queryParam("sort", "id,asc"))
                .andExpect(status().isOk());

        assertEquals(FeatureType.TECHNIQUE, entityDetailService.entityType);
        assertEquals("en", entityDetailService.language);
        assertEquals(1, entityDetailService.pageable.getPageNumber());
        assertEquals(3, entityDetailService.pageable.getPageSize());
        assertEquals("ASC", entityDetailService.pageable.getSort().getOrderFor("id").getDirection().name());
    }

    @Test
    void rejectsUnsupportedEntityType() throws Exception {
        mockMvc.perform(get("/api/catalogue/unknown/Anything"))
                .andExpect(status().isBadRequest());

        assertTrue(entityDetailService.notCalled());
    }

    private EntityOntologyDetails ontologyDetails(FeatureType entityType, String localName) {
        return new EntityOntologyDetails(
                entityType,
                "urn:test#" + localName,
                localName,
                localName,
                List.of(),
                null,
                "bg",
                List.of(),
                Map.of()
        );
    }

    private EntityCardDetails cardDetails(FeatureType entityType, String localName) {
        return new EntityCardDetails(
                entityType,
                "urn:test#" + localName,
                localName,
                localName,
                null,
                List.of()
        );
    }

    private static final class StubConceptCatalogService extends ConceptCatalogService {

        private ConceptCatalogQueryDto query;
        private Pageable pageable;
        private ConceptCatalogResultDetails result = new ConceptCatalogResultDetails(
                List.of(),
                new PageMetadataDetails(0, 1, 0, 0, true, true),
                List.of()
        );

        private StubConceptCatalogService() {
            super(null, null, null, null, null);
        }

        @Override
        public ConceptCatalogResultDetails search(ConceptCatalogQueryDto query, Pageable pageable) {
            this.query = query;
            this.pageable = pageable;
            return result;
        }
    }

    private static final class StubEntityDetailService extends EntityDetailService {

        private FeatureType entityType;
        private String localName;
        private String language;
        private Pageable pageable;
        private EntityDetailDetails result;

        private StubEntityDetailService() {
            super(null, null, null);
        }

        @Override
        public EntityDetailDetails findByLocalName(
                FeatureType entityType,
                String localName,
                String language,
                Pageable evidencePageable
        ) {
            this.entityType = entityType;
            this.localName = localName;
            this.language = language;
            this.pageable = evidencePageable;
            return result;
        }

        private boolean notCalled() {
            return entityType == null;
        }
    }
}
