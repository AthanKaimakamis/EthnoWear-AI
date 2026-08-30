package fmi.ethnowear.api.controller.archive;

import fmi.ethnowear.api.controller.archive.admin.AdminArchivePublicationController;
import fmi.ethnowear.api.exception.ArchiveApiExceptionHandler;
import fmi.ethnowear.application.dto.archive.item.ArchiveItemDetails;
import fmi.ethnowear.application.dto.archive.workflow.ArchivePublicationReadinessDetails;
import fmi.ethnowear.application.exception.ArchiveNotReadyForPublicationException;
import fmi.ethnowear.application.exception.ArchiveItemNotEditableException;
import fmi.ethnowear.application.exception.InvalidPublicationTransitionException;
import fmi.ethnowear.application.service.archive.workflow.ArchivePublicationService;
import fmi.ethnowear.domain.model.archive.ArchivePublicationRequirement;
import fmi.ethnowear.domain.model.archive.PublicationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminArchivePublicationControllerTest {

    private StubArchivePublicationService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = new StubArchivePublicationService();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AdminArchivePublicationController(service))
                .setControllerAdvice(new ArchiveApiExceptionHandler())
                .build();
    }

    @Test
    void returnsPublicationReadiness() throws Exception {
        mockMvc.perform(get(
                        "/api/admin/archive-items/41/publication-readiness"
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archiveItemId").value(41))
                .andExpect(jsonPath("$.publicationStatus").value("DRAFT"))
                .andExpect(jsonPath("$.ready").value(true));

        assertEquals(41L, service.validatedId);
    }

    @Test
    void submitsArchiveItem() throws Exception {
        mockMvc.perform(post("/api/admin/archive-items/42/submit"))
                .andExpect(status().isOk());

        assertEquals(42L, service.submittedId);
    }

    @Test
    void returnsArchiveItemToDraft() throws Exception {
        mockMvc.perform(post("/api/admin/archive-items/43/return-to-draft"))
                .andExpect(status().isOk());

        assertEquals(43L, service.returnedId);
    }

    @Test
    void publishesArchiveItem() throws Exception {
        mockMvc.perform(post("/api/admin/archive-items/44/publish"))
                .andExpect(status().isOk());

        assertEquals(44L, service.publishedId);
    }

    @Test
    void archivesArchiveItem() throws Exception {
        mockMvc.perform(post("/api/admin/archive-items/45/archive"))
                .andExpect(status().isOk());

        assertEquals(45L, service.archivedId);
    }

    @Test
    void reportsInvalidPublicationTransition() throws Exception {
        service.publishException = new InvalidPublicationTransitionException(
                46L,
                PublicationStatus.DRAFT,
                PublicationStatus.PUBLISHED
        );

        mockMvc.perform(post("/api/admin/archive-items/46/publish"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.archiveItemId").value(46))
                .andExpect(jsonPath("$.currentStatus").value("DRAFT"))
                .andExpect(jsonPath("$.targetStatus").value("PUBLISHED"));
    }

    @Test
    void reportsFailedPublicationRequirements() throws Exception {
        service.submitException = new ArchiveNotReadyForPublicationException(
                47L,
                List.of(
                        ArchivePublicationRequirement.LOCALIZED_TITLE,
                        ArchivePublicationRequirement.ONTOLOGY_CLASSIFICATION
                )
        );

        mockMvc.perform(post("/api/admin/archive-items/47/submit"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.archiveItemId").value(47))
                .andExpect(jsonPath("$.failedRequirements[0]")
                        .value("LOCALIZED_TITLE"))
                .andExpect(jsonPath("$.failedRequirements[1]")
                        .value("ONTOLOGY_CLASSIFICATION"));
    }

    @Test
    void reportsArchiveItemThatIsNotEditable() throws Exception {
        service.submitException = new ArchiveItemNotEditableException(
                48L,
                PublicationStatus.PUBLISHED
        );

        mockMvc.perform(post("/api/admin/archive-items/48/submit"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.archiveItemId").value(48))
                .andExpect(jsonPath("$.publicationStatus")
                        .value("PUBLISHED"));
    }

    private static final class StubArchivePublicationService
            extends ArchivePublicationService {

        private Long validatedId;
        private Long submittedId;
        private Long returnedId;
        private Long publishedId;
        private Long archivedId;
        private RuntimeException submitException;
        private RuntimeException publishException;

        private StubArchivePublicationService() {
            super(null, null, null);
        }

        @Override
        public ArchivePublicationReadinessDetails validate(Long archiveItemId) {
            validatedId = archiveItemId;
            return new ArchivePublicationReadinessDetails(
                    archiveItemId,
                    PublicationStatus.DRAFT,
                    true,
                    List.of()
            );
        }

        @Override
        public ArchiveItemDetails submit(Long archiveItemId) {
            if(submitException != null)
                throw submitException;

            submittedId = archiveItemId;
            return null;
        }

        @Override
        public ArchiveItemDetails returnToDraft(Long archiveItemId) {
            returnedId = archiveItemId;
            return null;
        }

        @Override
        public ArchiveItemDetails publish(Long archiveItemId) {
            if(publishException != null)
                throw publishException;

            publishedId = archiveItemId;
            return null;
        }

        @Override
        public ArchiveItemDetails archive(Long archiveItemId) {
            archivedId = archiveItemId;
            return null;
        }
    }
}
