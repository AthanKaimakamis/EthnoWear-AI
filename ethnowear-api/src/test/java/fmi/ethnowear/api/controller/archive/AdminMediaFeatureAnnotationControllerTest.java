package fmi.ethnowear.api.controller.archive;

import com.fasterxml.jackson.databind.ObjectMapper;
import fmi.ethnowear.api.controller.archive.admin.AdminMediaFeatureAnnotationController;
import fmi.ethnowear.application.dto.archive.media.MediaFeatureAnnotationDetails;
import fmi.ethnowear.application.dto.archive.media.MediaFeatureAnnotationWriteDto;
import fmi.ethnowear.api.exception.ArchiveApiExceptionHandler;
import fmi.ethnowear.domain.model.archive.MediaFeatureAnnotationType;
import fmi.ethnowear.application.service.archive.media.attachment.MediaFeatureAnnotationService;
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

class AdminMediaFeatureAnnotationControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private StubMediaFeatureAnnotationService annotationService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        annotationService = new StubMediaFeatureAnnotationService();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new TestAdminMediaFeatureAnnotationController(annotationService))
                .setControllerAdvice(new ArchiveApiExceptionHandler())
                .build();
    }

    private static final class TestAdminMediaFeatureAnnotationController
            extends AdminMediaFeatureAnnotationController {

        private TestAdminMediaFeatureAnnotationController(
                MediaFeatureAnnotationService service
        ) {
            super(service);
        }
    }

    @Test
    void createsAnnotationWithLocationHeader() throws Exception {
        annotationService.createResult = details(18L);

        mockMvc.perform(post("/api/admin/media-feature-annotations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(input())))
                .andExpect(status().isCreated())
                .andExpect(header().string(
                        "Location",
                        "http://localhost/api/admin/media-feature-annotations/18"
                ))
                .andExpect(jsonPath("$.id").value(18));
    }

    @Test
    void rejectsAnnotationWithoutRequiredRelationships() throws Exception {
        mockMvc.perform(post("/api/admin/media-feature-annotations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.archiveItemMediaId").exists())
                .andExpect(jsonPath("$.fields.archiveItemFeatureId").exists())
                .andExpect(jsonPath("$.fields.annotationType").exists());
    }

    @Test
    void deletesAnnotationWithoutResponseBody() throws Exception {
        mockMvc.perform(delete("/api/admin/media-feature-annotations/31"))
                .andExpect(status().isNoContent());

        assertEquals(31L, annotationService.deletedId);
    }

    private MediaFeatureAnnotationWriteDto input() {
        return new MediaFeatureAnnotationWriteDto(
                4L,
                7L,
                MediaFeatureAnnotationType.VISIBLE_IN_IMAGE,
                null,
                null,
                null,
                null,
                "Visible ornament"
        );
    }

    private MediaFeatureAnnotationDetails details(Long id) {
        MediaFeatureAnnotationWriteDto input = input();

        return new MediaFeatureAnnotationDetails(
                id,
                input.archiveItemMediaId(),
                input.archiveItemFeatureId(),
                input.annotationType(),
                input.x(),
                input.y(),
                input.width(),
                input.height(),
                input.note(),
                null,
                null
        );
    }

    private static final class StubMediaFeatureAnnotationService
            extends MediaFeatureAnnotationService {

        private MediaFeatureAnnotationDetails createResult;
        private Long deletedId;

        private StubMediaFeatureAnnotationService() {
            super(null, null, null, null);
        }

        @Override
        public MediaFeatureAnnotationDetails create(MediaFeatureAnnotationWriteDto input) {
            return createResult;
        }

        @Override
        public void delete(Long id) {
            deletedId = id;
        }
    }
}
