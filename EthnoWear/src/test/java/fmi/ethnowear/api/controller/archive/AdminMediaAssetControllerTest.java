package fmi.ethnowear.api.controller.archive;

import com.fasterxml.jackson.databind.ObjectMapper;
import fmi.ethnowear.api.controller.archive.admin.AdminMediaAssetController;
import fmi.ethnowear.api.exception.ArchiveApiExceptionHandler;
import fmi.ethnowear.application.dto.archive.media.MediaAssetDetails;
import fmi.ethnowear.application.dto.archive.media.MediaAssetMetadataWriteDto;
import fmi.ethnowear.application.service.archive.media.MediaAssetService;
import fmi.ethnowear.domain.model.archive.MediaType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
    void updatesOnlyCuratorMetadata() throws Exception {
        mediaAssetService.updateResult = details(14L, 9L, "Curator description");

        mockMvc.perform(patch("/api/admin/media-assets/14")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new MediaAssetMetadataWriteDto(9L, "Curator description")
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(14))
                .andExpect(jsonPath("$.sourceReferenceId").value(9))
                .andExpect(jsonPath("$.description").value("Curator description"));

        assertEquals(14L, mediaAssetService.updatedId);
        assertEquals(9L, mediaAssetService.updatedInput.sourceReferenceId());
    }

    @Test
    void doesNotExposeGenericCreateOrUpdateEndpoints() throws Exception {
        String body = objectMapper.writeValueAsString(
                new MediaAssetMetadataWriteDto(null, "Description")
        );

        mockMvc.perform(post("/api/admin/media-assets")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isMethodNotAllowed());

        mockMvc.perform(put("/api/admin/media-assets/14")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void deletesMediaAssetWithoutResponseBody() throws Exception {
        mockMvc.perform(delete("/api/admin/media-assets/27"))
                .andExpect(status().isNoContent());

        assertEquals(27L, mediaAssetService.deletedId);
    }

    private MediaAssetDetails details(Long id, Long sourceReferenceId, String description) {
        return new MediaAssetDetails(
                id,
                sourceReferenceId,
                "embroidery.jpg",
                "archive/embroidery.jpg",
                null,
                "image/jpeg",
                MediaType.IMAGE,
                1200,
                800,
                250000L,
                "checksum",
                "thumbnails/embroidery.jpg",
                description,
                null,
                null
        );
    }

    private static final class StubMediaAssetService extends MediaAssetService {

        private MediaAssetDetails updateResult;
        private MediaAssetMetadataWriteDto updatedInput;
        private Long updatedId;
        private Long deletedId;

        private StubMediaAssetService() {
            super(null, null, null, null, null);
        }

        @Override
        public MediaAssetDetails updateMetadata(Long id, MediaAssetMetadataWriteDto input) {
            updatedId = id;
            updatedInput = input;
            return updateResult;
        }

        @Override
        public void delete(Long id) {
            deletedId = id;
        }
    }
}
