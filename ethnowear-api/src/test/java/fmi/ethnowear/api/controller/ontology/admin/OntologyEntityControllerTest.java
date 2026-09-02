package fmi.ethnowear.api.controller.ontology.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import fmi.ethnowear.api.exception.OntologyAdminExceptionHandler;
import fmi.ethnowear.application.dto.ontology.admin.OntologyEntityCommand;
import fmi.ethnowear.application.dto.ontology.admin.OntologyEntityDetails;
import fmi.ethnowear.application.dto.ontology.admin.RegionDerivedTypeSynchronizationDetails;
import fmi.ethnowear.application.exception.OntologyEntityException;
import fmi.ethnowear.application.port.ontology.admin.OntologyEntityAdminPort;
import fmi.ethnowear.domain.model.ontology.OntologyEntityKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OntologyEntityControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private StubOntologyEntityAdminPort service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = new StubOntologyEntityAdminPort();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new OntologyEntityController(service))
                .setControllerAdvice(new OntologyAdminExceptionHandler())
                .build();
    }

    @Test
    void createsRegionAndReturnsGeneratedTypeCompatibleResponse() throws Exception {
        service.createResult = details("TestRegion");

        mockMvc.perform(post("/api/admin/ontology/regions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(command("TestRegion"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.localName").value("TestRegion"));
    }

    @Test
    void returnsStructuredSynchronizationCounts() throws Exception {
        service.synchronizationResult =
                new RegionDerivedTypeSynchronizationDetails(1, 2, 3);

        mockMvc.perform(post("/api/admin/ontology/regions/synchronize-derived-types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(1))
                .andExpect(jsonPath("$.updated").value(2))
                .andExpect(jsonPath("$.unchanged").value(3));
    }

    @Test
    void mapsOwnershipCollisionToStableConflict() throws Exception {
        service.synchronizationException = new OntologyEntityException(
                OntologyEntityException.Reason.ALREADY_EXISTS,
                "Generated ontology local name is already owned by an unmanaged resource"
        );

        mockMvc.perform(post("/api/admin/ontology/regions/synchronize-derived-types"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ONTOLOGY_RESOURCE_CONFLICT"));
    }

    private OntologyEntityCommand command(String localName) {
        return new OntologyEntityCommand(
                localName,
                "Тестов регион",
                "Test region",
                List.of(),
                List.of(),
                null,
                null,
                null,
                null,
                Set.of(),
                Set.of(),
                Set.of()
        );
    }

    private OntologyEntityDetails details(String localName) {
        return new OntologyEntityDetails(
                "urn:test#" + localName,
                localName,
                "Тестов регион",
                "Test region",
                List.of(),
                List.of(),
                null,
                null,
                null,
                null,
                Set.of(),
                Set.of(),
                Set.of()
        );
    }

    private static final class StubOntologyEntityAdminPort
            implements OntologyEntityAdminPort {

        private OntologyEntityDetails createResult;
        private RegionDerivedTypeSynchronizationDetails synchronizationResult =
                RegionDerivedTypeSynchronizationDetails.empty();
        private RuntimeException synchronizationException;

        @Override
        public List<OntologyEntityDetails> list(OntologyEntityKind kind) {
            return List.of();
        }

        @Override
        public OntologyEntityDetails get(OntologyEntityKind kind, String localName) {
            throw new UnsupportedOperationException();
        }

        @Override
        public OntologyEntityDetails create(
                OntologyEntityKind kind,
                OntologyEntityCommand command
        ) {
            return createResult;
        }

        @Override
        public OntologyEntityDetails update(
                OntologyEntityKind kind,
                String localName,
                OntologyEntityCommand command
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void delete(OntologyEntityKind kind, String localName) {
        }

        @Override
        public RegionDerivedTypeSynchronizationDetails synchronizeRegionDerivedTypes() {
            if (synchronizationException != null)
                throw synchronizationException;
            return synchronizationResult;
        }
    }
}
