package fmi.ethnowear.api.controller.archive;

import com.fasterxml.jackson.databind.ObjectMapper;
import fmi.ethnowear.api.controller.archive.admin.AdminKnowledgeChunkController;
import fmi.ethnowear.application.dto.archive.knowledge.KnowledgeChunkDetails;
import fmi.ethnowear.application.dto.archive.knowledge.KnowledgeChunkWriteDto;
import fmi.ethnowear.api.exception.ArchiveApiExceptionHandler;
import fmi.ethnowear.domain.model.archive.KnowledgeChunkType;
import fmi.ethnowear.domain.model.document.indexing.IndexingState;
import fmi.ethnowear.domain.model.document.provenance.ProvenanceTrustState;
import fmi.ethnowear.domain.model.document.review.ReviewState;
import fmi.ethnowear.domain.model.document.indexing.SourceTextType;
import fmi.ethnowear.domain.model.document.review.TranscriptionApprovalState;
import fmi.ethnowear.application.service.archive.knowledge.KnowledgeChunkService;
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

class AdminKnowledgeChunkControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private StubKnowledgeChunkService chunkService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        chunkService = new StubKnowledgeChunkService();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AdminKnowledgeChunkController(chunkService))
                .setControllerAdvice(new ArchiveApiExceptionHandler())
                .build();
    }

    @Test
    void createsKnowledgeChunkWithLocationHeader() throws Exception {
        chunkService.createResult = details(22L);

        mockMvc.perform(post("/api/admin/knowledge-chunks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(input())))
                .andExpect(status().isCreated())
                .andExpect(header().string(
                        "Location",
                        "http://localhost/api/admin/knowledge-chunks/22"
                ))
                .andExpect(jsonPath("$.id").value(22));
    }

    @Test
    void rejectsKnowledgeChunkWithoutRequiredContent() throws Exception {
        mockMvc.perform(post("/api/admin/knowledge-chunks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.chunkType").exists())
                .andExpect(jsonPath("$.fields.language").exists())
                .andExpect(jsonPath("$.fields.content").exists());
    }

    @Test
    void deletesKnowledgeChunkWithoutResponseBody() throws Exception {
        mockMvc.perform(delete("/api/admin/knowledge-chunks/36"))
                .andExpect(status().isNoContent());

        assertEquals(36L, chunkService.deletedId);
    }

    private KnowledgeChunkWriteDto input() {
        return new KnowledgeChunkWriteDto(
                KnowledgeChunkType.GENERAL,
                null,
                null,
                "en",
                "Traditional embroidery knowledge.",
                null
        );
    }

    private KnowledgeChunkDetails details(Long id) {
        KnowledgeChunkWriteDto input = input();

        return new KnowledgeChunkDetails(
                id,
                input.sourceReferenceId(),
                null,
                null,
                input.chunkType(),
                input.ontologyIri(),
                input.ontologyLocalName(),
                input.language(),
                input.content(),
                SourceTextType.MANUAL_EXCERPT,
                null,
                "content-hash",
                ReviewState.REVIEW_REQUIRED,
                TranscriptionApprovalState.NOT_REQUIRED,
                ProvenanceTrustState.UNKNOWN,
                IndexingState.NOT_ELIGIBLE,
                null,
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

    private static final class StubKnowledgeChunkService extends KnowledgeChunkService {

        private KnowledgeChunkDetails createResult;
        private Long deletedId;

        private StubKnowledgeChunkService() {
            super(null, null, null);
        }

        @Override
        public KnowledgeChunkDetails create(KnowledgeChunkWriteDto input) {
            return createResult;
        }

        @Override
        public void delete(Long id) {
            deletedId = id;
        }
    }
}
