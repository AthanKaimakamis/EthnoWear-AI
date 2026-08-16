package fmi.ethnowear.api.controller.archive;

import com.fasterxml.jackson.databind.ObjectMapper;
import fmi.ethnowear.api.controller.archive.admin.AdminMediaAssetController;
import fmi.ethnowear.application.dto.archive.media.MediaAssetDetails;
import fmi.ethnowear.application.dto.archive.media.MediaAssetWriteDto;
import fmi.ethnowear.api.exception.ArchiveApiExceptionHandler;
import fmi.ethnowear.domain.model.archive.MediaType;
import fmi.ethnowear.application.service.archive.media.MediaAssetService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminMediaAssetControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private StubMediaAssetService mediaAssetService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mediaAssetService = new StubMediaAssetService();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AdminMediaAssetController(mediaAssetService, null))
                .setControllerAdvice(new ArchiveApiExceptionHandler())
                .build();
    }

    @Test
    void createsMediaAssetWithLocationHeader() throws Exception {
        mediaAssetService.createResult = details(14L);

        mockMvc.perform(post("/api/admin/media-assets")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(input())))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/admin/media-assets/14"))
                .andExpect(jsonPath("$.id").value(14));
    }

    @Test
    void rejectsMediaAssetWithoutMediaType() throws Exception {
        mockMvc.perform(post("/api/admin/media-assets")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.mediaType").exists());
    }

    @Test
    void deletesMediaAssetWithoutResponseBody() throws Exception {
        mockMvc.perform(delete("/api/admin/media-assets/27"))
                .andExpect(status().isNoContent());

        assertEquals(27L, mediaAssetService.deletedId);
    }

    private MediaAssetWriteDto input() {
        return new MediaAssetWriteDto(
                null,
                "embroidery.jpg",
                "/archive/embroidery.jpg",
                null,
                "image/jpeg",
                MediaType.IMAGE,
                1200,
                800,
                250000L,
                null
        );
    }

    private MediaAssetDetails details(Long id) {
        MediaAssetWriteDto input = input();

        return new MediaAssetDetails(
                id,
                input.sourceReferenceId(),
                input.fileName(),
                input.filePath(),
                input.storageUrl(),
                input.mimeType(),
                input.mediaType(),
                input.width(),
                input.height(),
                input.sizeBytes(),
                input.checksum(),
                null,
                null,
                null,
                null
        );
    }

    private static final class StubMediaAssetService extends MediaAssetService {

        private MediaAssetDetails createResult;
        private Long deletedId;

        private StubMediaAssetService() {
            super(null, null, null, null, null);
        }

        @Override
        public MediaAssetDetails create(MediaAssetWriteDto input) {
            return createResult;
        }

        @Override
        public void delete(Long id) {
            deletedId = id;
        }
    }
}
