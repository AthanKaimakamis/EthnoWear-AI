package fmi.ethnowear.api.controller.archive;

import com.fasterxml.jackson.databind.ObjectMapper;
import fmi.ethnowear.api.controller.archive.admin.AdminArchiveItemController;
import fmi.ethnowear.api.controller.archive.admin.AdminArchiveItemFeatureController;
import fmi.ethnowear.application.dto.archive.item.ArchiveItemDetails;
import fmi.ethnowear.application.dto.archive.item.ArchiveItemFeatureDetails;
import fmi.ethnowear.application.dto.archive.item.ArchiveItemFeatureWriteDto;
import fmi.ethnowear.application.dto.archive.item.ArchiveItemWriteDto;
import fmi.ethnowear.api.exception.ArchiveApiExceptionHandler;
import fmi.ethnowear.domain.model.archive.ArchiveType;
import fmi.ethnowear.domain.model.archive.PublicationStatus;
import fmi.ethnowear.domain.model.ontology.FeatureType;
import fmi.ethnowear.domain.model.archive.TrustedLevel;
import fmi.ethnowear.application.service.archive.item.ArchiveItemFeatureService;
import fmi.ethnowear.application.service.archive.item.ArchiveItemService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminArchiveItemControllersTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private StubArchiveItemService itemService;
    private StubArchiveItemFeatureService featureService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        itemService = new StubArchiveItemService();
        featureService = new StubArchiveItemFeatureService();

        mockMvc = MockMvcBuilders.standaloneSetup(
                        new AdminArchiveItemController(itemService),
                        new AdminArchiveItemFeatureController(featureService)
                )
                .setControllerAdvice(new ArchiveApiExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
    }

    @Test
    void createsArchiveItemWithLocationHeader() throws Exception {
        itemService.createResult = itemDetails(41L);

        mockMvc.perform(post("/api/admin/archive-items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(itemInput())))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/admin/archive-items/41"))
                .andExpect(jsonPath("$.id").value(41));
    }

    @Test
    void acceptsMotifAndTechniqueExampleArchiveTypes() throws Exception {
        itemService.createResult = itemDetails(41L);

        for (ArchiveType archiveType : List.of(
                ArchiveType.MOTIF_EXAMPLE,
                ArchiveType.TECHNIQUE_EXAMPLE
        )) {
            ArchiveItemWriteDto input = itemInput(archiveType);
            mockMvc.perform(post("/api/admin/archive-items")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(input)))
                    .andExpect(status().isCreated());

            assertEquals(archiveType, itemService.createInput.archiveType());
        }
    }

    @Test
    void forwardsArchiveItemPagination() throws Exception {
        itemService.page = new PageImpl<>(
                List.of(itemDetails(1L)),
                PageRequest.of(2, 7),
                15
        );

        mockMvc.perform(get("/api/admin/archive-items")
                        .queryParam("page", "2")
                        .queryParam("size", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1));

        assertEquals(2, itemService.pageable.getPageNumber());
        assertEquals(7, itemService.pageable.getPageSize());
    }

    @Test
    void createsArchiveItemFeatureWithLocationHeader() throws Exception {
        featureService.createResult = featureDetails(17L);

        mockMvc.perform(post("/api/admin/archive-item-features")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(featureInput())))
                .andExpect(status().isCreated())
                .andExpect(header().string(
                        "Location",
                        "http://localhost/api/admin/archive-item-features/17"
                ))
                .andExpect(jsonPath("$.id").value(17));
    }

    @Test
    void rejectsInvalidArchiveItemFeature() throws Exception {
        mockMvc.perform(post("/api/admin/archive-item-features")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.fields.archiveItemId").exists())
                .andExpect(jsonPath("$.fields.featureType").exists());
    }

    @Test
    void deletesArchiveItemFeatureWithoutResponseBody() throws Exception {
        mockMvc.perform(delete("/api/admin/archive-item-features/29"))
                .andExpect(status().isNoContent());

        assertEquals(29L, featureService.deletedId);
    }

    private ArchiveItemWriteDto itemInput() {
        return itemInput(ArchiveType.EMBROIDERY_SAMPLE);
    }

    private ArchiveItemWriteDto itemInput(ArchiveType archiveType) {
        return new ArchiveItemWriteDto(
                3L,
                null,
                null,
                "Архивен запис",
                null,
                null,
                null,
                archiveType,
                null,
                null,
                null,
                TrustedLevel.VERIFIED,
                null,
                null,
                null,
                null
        );
    }

    private ArchiveItemDetails itemDetails(Long id) {
        ArchiveItemWriteDto input = itemInput();

        return new ArchiveItemDetails(
                id,
                input.sourceReferenceId(),
                input.collectionId(),
                input.inventoryNumber(),
                input.titleBg(),
                input.titleEn(),
                input.descriptionBg(),
                input.descriptionEn(),
                input.archiveType(),
                input.periodText(),
                input.originText(),
                input.currentLocation(),
                input.trustedLevel(),
                PublicationStatus.DRAFT,
                input.ontologyRegionIri(),
                input.ontologyRegionLocalName(),
                input.ontologyRegionalEmbroideryIri(),
                input.ontologyRegionalEmbroideryLocalName(),
                null,
                null,
                null,
                null,
                null
        );
    }

    private ArchiveItemFeatureWriteDto featureInput() {
        return new ArchiveItemFeatureWriteDto(
                3L,
                FeatureType.TECHNIQUE,
                "urn:test#ReturnStitch",
                "ReturnStitch",
                BigDecimal.ONE,
                true,
                null,
                null
        );
    }

    private ArchiveItemFeatureDetails featureDetails(Long id) {
        ArchiveItemFeatureWriteDto input = featureInput();

        return new ArchiveItemFeatureDetails(
                id,
                input.archiveItemId(),
                input.featureType(),
                input.ontologyIri(),
                input.ontologyLocalName(),
                input.confidence(),
                input.validated(),
                input.notes(),
                input.sourceReferenceId(),
                null,
                null
        );
    }

    private static final class StubArchiveItemService extends ArchiveItemService {

        private Page<ArchiveItemDetails> page = Page.empty();
        private Pageable pageable;
        private ArchiveItemDetails createResult;
        private ArchiveItemWriteDto createInput;

        private StubArchiveItemService() {
            super(null, null, null, null, null, null);
        }

        @Override
        public Page<ArchiveItemDetails> findAll(Pageable pageable) {
            this.pageable = pageable;
            return page;
        }

        @Override
        public ArchiveItemDetails create(ArchiveItemWriteDto input) {
            createInput = input;
            return createResult;
        }
    }

    private static final class StubArchiveItemFeatureService extends ArchiveItemFeatureService {

        private ArchiveItemFeatureDetails createResult;
        private Long deletedId;

        private StubArchiveItemFeatureService() {
            super(null, null, null, null, null, null);
        }

        @Override
        public ArchiveItemFeatureDetails create(ArchiveItemFeatureWriteDto input) {
            return createResult;
        }

        @Override
        public void delete(Long id) {
            deletedId = id;
        }
    }
}
