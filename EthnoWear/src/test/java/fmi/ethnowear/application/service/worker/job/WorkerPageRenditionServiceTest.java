package fmi.ethnowear.application.service.worker.job;

import fmi.ethnowear.application.dto.archive.media.MediaAssetDetails;
import fmi.ethnowear.application.dto.archive.media.MediaUploadRequest;
import fmi.ethnowear.application.dto.worker.rendition.*;
import fmi.ethnowear.application.exception.ResourceNotFoundException;
import fmi.ethnowear.application.exception.WorkerPayloadTooLargeException;
import fmi.ethnowear.application.exception.WorkerRenditionConflictException;
import fmi.ethnowear.application.exception.WorkerUnsupportedMediaTypeException;
import fmi.ethnowear.config.WorkerApiProperties;
import fmi.ethnowear.application.service.archive.media.storage.*;
import fmi.ethnowear.persistence.jpa.entity.MediaAsset;
import fmi.ethnowear.persistence.jpa.entity.document.*;
import fmi.ethnowear.persistence.jpa.repository.MediaAssetRepository;
import fmi.ethnowear.persistence.jpa.repository.document.*;
import fmi.ethnowear.testutil.EntityTestUtils;
import fmi.ethnowear.util.ContentHashUtils;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.util.unit.DataSize;

import java.time.Duration;
import java.util.Optional;

import static fmi.ethnowear.support.RepositoryTestProxies.proxy;
import static fmi.ethnowear.support.RepositoryTestProxies.rejecting;
import static fmi.ethnowear.support.WorkerTestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class WorkerPageRenditionServiceTest {

    @Test
    void identicalRetryIsIdempotentAndConflictingRetryIsRejected() {
        Document document = document(7L);
        DocumentProcessingJob job = activePageExtractionJob(11L, document);
        DocumentPage page = page(document, 21L);
        DocumentPageMedia accepted = accepted(page, 41L, 31L, ContentHashUtils.sha256("image"));
        WorkerPageRenditionService service = service(job, page, Optional.of(accepted));

        WorkerPageRenditionDetails repeated = service.upload(
                11L,
                21L,
                credentials(),
                command(),
                file("image")
        );

        assertTrue(repeated.existing());
        assertEquals(41L, repeated.pageMediaId());
        assertThrows(
                WorkerRenditionConflictException.class,
                () -> service.upload(11L, 21L, credentials(), command(), file("different"))
        );
    }

    @Test
    void rejectsPageOutsideTheClaimedDocumentBeforeWritingAFile() {
        Document document = document(7L);
        DocumentProcessingJob job = activePageExtractionJob(11L, document);

        assertThrows(
                ResourceNotFoundException.class,
                () -> service(job, null, Optional.empty()).upload(
                        11L,
                        99L,
                        credentials(),
                        command(),
                        file("image")
                )
        );
    }

    @Test
    void rejectsOversizedRenditionWithPayloadTooLargeError() {
        DocumentProcessingJob job = activePageExtractionJob(11L, document(7L));

        assertThrows(
                WorkerPayloadTooLargeException.class,
                () -> service(job, null, Optional.empty(), propertiesWithMaximumSize(3)).upload(
                        11L,
                        21L,
                        credentials(),
                        command(),
                        file("image")
                )
        );
    }

    @Test
    void rejectsUnsupportedRenditionMediaType() {
        DocumentProcessingJob job = activePageExtractionJob(11L, document(7L));
        MockMultipartFile gif = new MockMultipartFile(
                "file",
                "worker.gif",
                "image/gif",
                "image".getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );

        assertThrows(
                WorkerUnsupportedMediaTypeException.class,
                () -> service(job, null, Optional.empty()).upload(
                        11L,
                        21L,
                        credentials(),
                        command(),
                        gif
                )
        );
    }

    @Test
    void rejectsRenditionExceedingMaximumTotalPixels() {
        DocumentProcessingJob job = activePageExtractionJob(11L, document(7L));
        WorkerPageRenditionCommand oversized = new WorkerPageRenditionCommand(
                0,
                1,
                WorkerPageRenditionType.PDF_PAGE_RENDER,
                300,
                WorkerColorMode.RGB,
                11000,
                10000,
                "test-renderer",
                "1.0"
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> service(job, null, Optional.empty()).upload(
                        11L,
                        21L,
                        credentials(),
                        oversized,
                        file("image")
                )
        );
    }

    private WorkerPageRenditionService service(
            DocumentProcessingJob job,
            DocumentPage page,
            Optional<DocumentPageMedia> existing
    ) {
        return service(job, page, existing, properties());
    }

    private WorkerPageRenditionService service(
            DocumentProcessingJob job,
            DocumentPage page,
            Optional<DocumentPageMedia> existing,
            WorkerApiProperties properties
    ) {
        DocumentPageRepository pages = proxy(
                DocumentPageRepository.class,
                (ignored, method, arguments) -> {
                    if(method.getName().equals("findByIdAndDocument_Id"))
                        return page == null ? Optional.empty() : Optional.of(page);

                    throw new AssertionError("Unexpected page repository call: " + method.getName());
                }
        );
        DocumentPageMediaRepository pageMedia = proxy(
                DocumentPageMediaRepository.class,
                (ignored, method, arguments) -> {
                    if(method.getName().equals(
                            "findByDocumentPage_IdAndRenditionTypeAndProducingJob_IdAndProducingAttempt"
                    ))
                        return existing;

                    throw new AssertionError("Unexpected page-media repository call: " + method.getName());
                }
        );
        MediaUploadService uploads = new MediaUploadService(null, null, null, null, null, null, null, null) {
            @Override
            public MediaAssetDetails upload(
                    MultipartFile file,
                    MediaUploadRequest request,
                    MediaUploadDestination destination
            ) {
                throw new AssertionError("Idempotent or rejected upload must not write a file");
            }
        };

        return new WorkerPageRenditionService(
                loader(job),
                pages,
                pageMedia,
                rejecting(MediaAssetRepository.class),
                uploads,
                new MediaFileHasher(),
                properties
        );
    }

    private WorkerApiProperties propertiesWithMaximumSize(long bytes) {
        WorkerApiProperties defaults = properties();
        return new WorkerApiProperties(
                defaults.enabled(),
                defaults.token(),
                defaults.minimumLease(),
                defaults.defaultLease(),
                defaults.maximumLease(),
                defaults.heartbeatInterval(),
                defaults.recoveryInterval(),
                defaults.jobTimeout(),
                defaults.maximumInputSize(),
                DataSize.ofBytes(bytes),
                defaults.maximumPageCount(),
                defaults.renderDpi(),
                defaults.maximumPixelWidth(),
                defaults.maximumPixelHeight(),
                defaults.maximumPagePixels()
        );
    }

    private WorkerPageRenditionCommand command() {
        return new WorkerPageRenditionCommand(
                0,
                1,
                WorkerPageRenditionType.PDF_PAGE_RENDER,
                300,
                WorkerColorMode.RGB,
                1200,
                1600,
                "test-renderer",
                "1.0"
        );
    }

    private MockMultipartFile file(String content) {
        return new MockMultipartFile(
                "file",
                "worker-controlled-name.png",
                "image/png",
                content.getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );
    }

    private DocumentPage page(Document document, long id) {
        DocumentPage page = new DocumentPage();
        EntityTestUtils.setId(page, id);
        page.setDocument(document);
        page.setPdfPageIndex(0);
        page.setPageSequence(1);
        return page;
    }

    private DocumentPageMedia accepted(
            DocumentPage page,
            long pageMediaId,
            long mediaAssetId,
            String hash
    ) {
        MediaAsset asset = new MediaAsset();
        EntityTestUtils.setId(asset, mediaAssetId);
        DocumentPageMedia media = new DocumentPageMedia();
        EntityTestUtils.setId(media, pageMediaId);
        media.setDocumentPage(page);
        media.setMediaAsset(asset);
        media.setRenditionHash(hash);
        return media;
    }
}
