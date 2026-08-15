package fmi.ethnowear.api.controller.archive;

import fmi.ethnowear.application.dto.archive.query.ArchiveItemDetailDetails;
import fmi.ethnowear.application.dto.archive.query.RegionalEmbroideryArchiveOverviewDetails;
import fmi.ethnowear.api.exception.ArchiveApiExceptionHandler;
import fmi.ethnowear.application.exception.ResourceNotFoundException;
import fmi.ethnowear.application.service.archive.media.MediaDelivery;
import fmi.ethnowear.application.service.archive.media.MediaDeliveryService;
import fmi.ethnowear.application.service.archive.query.ArchiveItemDetailService;
import fmi.ethnowear.application.service.archive.query.RegionalEmbroideryArchiveService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PublicArchiveControllerTest {

    private StubRegionalEmbroideryArchiveService regionalEmbroideryService;
    private StubArchiveItemDetailService detailService;
    private StubMediaDeliveryService mediaDeliveryService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        regionalEmbroideryService = new StubRegionalEmbroideryArchiveService();
        detailService = new StubArchiveItemDetailService();
        mediaDeliveryService = new StubMediaDeliveryService();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new PublicArchiveController(
                        regionalEmbroideryService,
                        detailService,
                        mediaDeliveryService
                ))
                .setControllerAdvice(new ArchiveApiExceptionHandler())
                .build();
    }

    @Test
    void usesDefaultLanguageAndPreviewSize() throws Exception {
        mockMvc.perform(get("/api/archive/regional-embroideries"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.language").value("bg"))
                .andExpect(jsonPath("$.sections").isArray());

        assertEquals("bg", regionalEmbroideryService.language);
        assertEquals(4, regionalEmbroideryService.previewSize);
    }

    @Test
    void forwardsRequestedLanguageAndPreviewSize() throws Exception {
        mockMvc.perform(get("/api/archive/regional-embroideries")
                        .param("language", "en")
                        .param("previewSize", "6"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.language").value("en"));

        assertEquals("en", regionalEmbroideryService.language);
        assertEquals(6, regionalEmbroideryService.previewSize);
    }

    @Test
    void forwardsArchiveItemIdentifier() throws Exception {
        mockMvc.perform(get("/api/archive/items/42"))
                .andExpect(status().isOk());

        assertEquals(42L, detailService.id);
    }

    @Test
    void redirectsToExternalMediaStorage() throws Exception {
        mediaDeliveryService.delivery = new MediaDelivery.Redirect(
                URI.create("https://cdn.example.org/archive/item.jpg")
        );

        mockMvc.perform(get("/api/archive/media/12"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("https://cdn.example.org/archive/item.jpg"));

        assertEquals(12L, mediaDeliveryService.id);
    }

    @Test
    void returnsLocalMediaWithContentHeaders() throws Exception {
        byte[] contentBytes = "image-content".getBytes(StandardCharsets.UTF_8);
        mediaDeliveryService.delivery = new MediaDelivery.Local(
                new ByteArrayResource(contentBytes),
                "image/jpeg",
                "archive-item.jpg",
                contentBytes.length
        );

        mockMvc.perform(get("/api/archive/media/13"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andExpect(content().bytes(contentBytes))
                .andExpect(header().longValue(
                        HttpHeaders.CONTENT_LENGTH,
                        contentBytes.length
                ))
                .andExpect(header().string(
                        HttpHeaders.CONTENT_DISPOSITION,
                        containsString("archive-item.jpg")
                ));
    }

    @Test
    void fallsBackToBinaryContentType() throws Exception {
        byte[] contentBytes = {1, 2, 3};
        mediaDeliveryService.delivery = new MediaDelivery.Local(
                new ByteArrayResource(contentBytes),
                "invalid media type",
                null,
                contentBytes.length
        );

        mockMvc.perform(get("/api/archive/media/14"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_OCTET_STREAM));
    }

    @Test
    void returnsNotFoundWhenMediaDoesNotExist() throws Exception {
        mediaDeliveryService.exception = new ResourceNotFoundException(
                "Media asset",
                15L
        );

        mockMvc.perform(get("/api/archive/media/15"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Media asset not found: 15"));
    }

    private static final class StubRegionalEmbroideryArchiveService
            extends RegionalEmbroideryArchiveService {

        private String language;
        private int previewSize;

        private StubRegionalEmbroideryArchiveService() {
            super(null, null, null);
        }

        @Override
        public RegionalEmbroideryArchiveOverviewDetails findOverview(
                String languageTag,
                int previewSize
        ) {
            this.language = languageTag;
            this.previewSize = previewSize;
            return new RegionalEmbroideryArchiveOverviewDetails(languageTag, List.of());
        }
    }

    private static final class StubArchiveItemDetailService
            extends ArchiveItemDetailService {

        private Long id;

        private StubArchiveItemDetailService() {
            super(null, null, null, null, null, null, null, null, null, null);
        }

        @Override
        public ArchiveItemDetailDetails findById(Long id) {
            this.id = id;
            return new ArchiveItemDetailDetails(null, null, List.of(), List.of());
        }
    }

    private static final class StubMediaDeliveryService
            extends MediaDeliveryService {

        private Long id;
        private MediaDelivery delivery;
        private RuntimeException exception;

        private StubMediaDeliveryService() {
            super(null, null);
        }

        @Override
        public MediaDelivery findById(Long id) {
            this.id = id;

            if (exception != null)
                throw exception;

            return delivery;
        }
    }
}
