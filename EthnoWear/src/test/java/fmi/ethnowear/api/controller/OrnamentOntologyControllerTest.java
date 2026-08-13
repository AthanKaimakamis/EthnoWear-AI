package fmi.ethnowear.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import fmi.ethnowear.ontology.OntologyTerms;
import fmi.ethnowear.application.exceptions.OrnamentAlreadyExistsException;
import fmi.ethnowear.ontology.admin.OrnamentCreateCommand;
import fmi.ethnowear.ontology.admin.OrnamentDetails;
import fmi.ethnowear.ontology.admin.OrnamentOntologyAdminService;
import fmi.ethnowear.ontology.admin.OrnamentUpdateCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OrnamentOntologyControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private StubOrnamentService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = new StubOrnamentService();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new OrnamentOntologyController(service))
                .setControllerAdvice(new OntologyAdminExceptionHandler())
                .build();
    }

    @Test
    void createsOrnament() throws Exception {
        OrnamentCreateCommand command = command("NewOrnament");
        service.createResult = details("NewOrnament");

        mockMvc.perform(post("/api/admin/ontology/ornaments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(command)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.localName").value("NewOrnament"));
    }

    @Test
    void returnsNotFoundForUnknownOrnament() throws Exception {
        service.getResult = Optional.empty();

        mockMvc.perform(get("/api/admin/ontology/ornaments/MissingOrnament"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void returnsConflictForDuplicateOrnament() throws Exception {
        OrnamentCreateCommand command = command("DuplicateOrnament");
        service.createException = new OrnamentAlreadyExistsException("DuplicateOrnament");

        mockMvc.perform(post("/api/admin/ontology/ornaments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(command)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void deletesOrnament() throws Exception {
        mockMvc.perform(delete("/api/admin/ontology/ornaments/OldOrnament"))
                .andExpect(status().isNoContent());

        org.junit.jupiter.api.Assertions.assertEquals("OldOrnament", service.deletedLocalName);
    }

    private OrnamentCreateCommand command(String localName) {
        return new OrnamentCreateCommand(
                localName,
                Set.of(OntologyTerms.Classes.GEOMETRIC_ORNAMENT),
                "орнамент",
                "ornament",
                List.of(),
                List.of(),
                null,
                null,
                Set.of()
        );
    }

    private OrnamentDetails details(String localName) {
        return new OrnamentDetails(
                "urn:test#" + localName,
                localName,
                Set.of(OntologyTerms.Classes.GEOMETRIC_ORNAMENT),
                "орнамент",
                "ornament",
                List.of(),
                List.of(),
                null,
                null,
                Set.of()
        );
    }

    private static final class StubOrnamentService extends OrnamentOntologyAdminService {

        private OrnamentDetails createResult;
        private RuntimeException createException;
        private Optional<OrnamentDetails> getResult = Optional.empty();
        private String deletedLocalName;

        private StubOrnamentService() {
            super(null);
        }

        @Override
        public OrnamentDetails create(OrnamentCreateCommand command) {
            if (createException != null) {
                throw createException;
            }
            return createResult;
        }

        @Override
        public Optional<OrnamentDetails> get(String localName) {
            return getResult;
        }

        @Override
        public OrnamentDetails update(String localName, OrnamentUpdateCommand command) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void delete(String localName) {
            deletedLocalName = localName;
        }
    }
}
