package fmi.ethnowear.api.controller.archive;

import com.fasterxml.jackson.databind.ObjectMapper;
import fmi.ethnowear.api.controller.archive.admin.AdminSourceReferenceController;
import fmi.ethnowear.api.dto.archive.source.SourceReferenceDetails;
import fmi.ethnowear.api.dto.archive.source.SourceReferenceWriteDto;
import fmi.ethnowear.application.service.archive.source.SourceReferenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminSourceReferenceControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private StubSourceReferenceService referenceService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        referenceService = new StubSourceReferenceService();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AdminSourceReferenceController(referenceService))
                .setControllerAdvice(new ArchiveApiExceptionHandler())
                .build();
    }

    @Test
    void createsSourceReferenceWithLocationHeader() throws Exception {
        referenceService.createResult = details(8L, 3L);
        SourceReferenceWriteDto request = new SourceReferenceWriteDto(
                3L,
                "Chapter 2",
                15,
                18,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        mockMvc.perform(post("/api/admin/source-references")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string(
                        "Location",
                        "http://localhost/api/admin/source-references/8"
                ))
                .andExpect(jsonPath("$.sourceId").value(3));
    }

    @Test
    void rejectsSourceReferenceWithoutSource() throws Exception {
        mockMvc.perform(post("/api/admin/source-references")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.sourceId").exists());
    }

    private SourceReferenceDetails details(Long id, Long sourceId) {
        return new SourceReferenceDetails(
                id,
                sourceId,
                "Chapter 2",
                15,
                18,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    private static final class StubSourceReferenceService extends SourceReferenceService {

        private SourceReferenceDetails createResult;

        private StubSourceReferenceService() {
            super(null, null, null, null);
        }

        @Override
        public SourceReferenceDetails create(SourceReferenceWriteDto input) {
            return createResult;
        }
    }
}
