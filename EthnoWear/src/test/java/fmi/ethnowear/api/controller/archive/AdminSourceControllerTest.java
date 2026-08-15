package fmi.ethnowear.api.controller.archive;

import com.fasterxml.jackson.databind.ObjectMapper;
import fmi.ethnowear.api.controller.archive.admin.AdminSourceController;
import fmi.ethnowear.application.dto.archive.source.SourceDetails;
import fmi.ethnowear.application.dto.archive.source.SourceWriteDto;
import fmi.ethnowear.api.exception.ArchiveApiExceptionHandler;
import fmi.ethnowear.domain.model.archive.SourceType;
import fmi.ethnowear.application.service.archive.source.SourceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminSourceControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private StubSourceService sourceService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        sourceService = new StubSourceService();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AdminSourceController(sourceService))
                .setControllerAdvice(new ArchiveApiExceptionHandler())
                .build();
    }

    @Test
    void createsSourceWithLocationHeader() throws Exception {
        sourceService.createResult = details(12L);

        mockMvc.perform(post("/api/admin/sources")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(input())))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/admin/sources/12"))
                .andExpect(jsonPath("$.id").value(12));
    }

    @Test
    void rejectsInvalidSource() throws Exception {
        mockMvc.perform(post("/api/admin/sources")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.title").exists())
                .andExpect(jsonPath("$.fields.sourceType").exists());
    }

    @Test
    void deletesSourceWithoutResponseBody() throws Exception {
        mockMvc.perform(delete("/api/admin/sources/23"))
                .andExpect(status().isNoContent());

        assertEquals(23L, sourceService.deletedId);
    }

    private SourceWriteDto input() {
        return new SourceWriteDto(
                "Bulgarian Embroidery",
                "Author",
                null,
                2024,
                SourceType.BOOK,
                "en",
                null,
                null,
                null,
                null,
                true
        );
    }

    private SourceDetails details(Long id) {
        SourceWriteDto input = input();

        return new SourceDetails(
                id,
                input.title(),
                input.author(),
                input.publisher(),
                input.year(),
                input.sourceType(),
                input.language(),
                input.filePath(),
                input.url(),
                input.isbn(),
                input.notes(),
                input.trusted(),
                null,
                null
        );
    }

    private static final class StubSourceService extends SourceService {

        private SourceDetails createResult;
        private Long deletedId;

        private StubSourceService() {
            super(null, null, null);
        }

        @Override
        public SourceDetails create(SourceWriteDto input) {
            return createResult;
        }

        @Override
        public void delete(Long id) {
            deletedId = id;
        }
    }
}
