package fmi.ethnowear.application.service.document.upload;

import fmi.ethnowear.application.dto.archive.media.MediaAssetDetails;
import fmi.ethnowear.application.dto.archive.media.MediaUploadRequest;
import fmi.ethnowear.application.dto.document.command.upload.*;
import fmi.ethnowear.application.service.archive.media.storage.MediaUploadDestination;
import fmi.ethnowear.application.service.archive.media.storage.MediaUploadService;
import fmi.ethnowear.application.service.document.processing.DocumentProcessingJobScheduler;
import fmi.ethnowear.domain.model.archive.MediaType;
import fmi.ethnowear.domain.model.document.DocumentType;
import fmi.ethnowear.domain.model.document.PageRole;
import fmi.ethnowear.domain.model.document.processing.ProcessingState;
import fmi.ethnowear.domain.model.document.provenance.ProvenanceStatus;
import fmi.ethnowear.domain.model.document.provenance.ProvenanceTrustState;
import fmi.ethnowear.persistence.jpa.entity.MediaAsset;
import fmi.ethnowear.persistence.jpa.entity.document.Document;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPage;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPageMedia;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentProcessingJob;
import fmi.ethnowear.persistence.jpa.repository.MediaAssetRepository;
import fmi.ethnowear.persistence.jpa.repository.SourceReferenceRepository;
import fmi.ethnowear.persistence.jpa.repository.SourceRepository;
import fmi.ethnowear.persistence.jpa.repository.document.*;
import fmi.ethnowear.testutil.EntityTestUtils;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static fmi.ethnowear.support.RepositoryTestProxies.proxy;
import static fmi.ethnowear.support.RepositoryTestProxies.rejecting;
import static org.junit.jupiter.api.Assertions.*;

class DocumentUploadWorkflowServicesTest {

    @Test
    void uploadsPdfToDocumentHierarchyAndQueuesExtractionOnly() {
        AtomicReference<Document> savedDocument = new AtomicReference<>();
        DocumentRepository documents = documentRepository(savedDocument, 7L);
        MediaAsset media = entity(new MediaAsset(), 17L);
        MediaAssetRepository mediaAssets = proxy(
                MediaAssetRepository.class,
                (ignored, method, arguments) -> {
                    if (method.getName().equals("findById"))
                        return Optional.of(media);
                    throw new AssertionError("Unexpected media call: " + method.getName());
                }
        );
        CapturingUploadService uploads = new CapturingUploadService(mediaDetails(17L, MediaType.PDF));
        AtomicReference<Document> queuedDocument = new AtomicReference<>();
        AtomicReference<MediaAsset> queuedMedia = new AtomicReference<>();
        DocumentProcessingJobScheduler jobs = jobs(27L, queuedDocument, null, queuedMedia);

        PdfDocumentUploadService service = new PdfDocumentUploadService(
                documents,
                mediaAssets,
                uploads,
                new NoOpUploadValidator(),
                references(),
                new DocumentCreationFactory(),
                jobs,
                new DocumentUploadStatePolicy(),
                null
        );

        DocumentUploadDetails result = service.upload(
                new PdfDocumentUploadCommand(
                        metadata("Source PDF"),
                        DocumentType.PDF_DOCUMENT,
                        ProvenanceStatus.UNKNOWN_SOURCE,
                        ProvenanceTrustState.UNKNOWN,
                        "Immutable source PDF"
                ),
                file("source.pdf", "application/pdf")
        );

        assertEquals(7L, result.documentId());
        assertEquals(17L, result.mediaAssetId());
        assertEquals(27L, result.processingJobId());
        assertNull(result.documentPageId());
        assertNull(result.documentPageMediaId());
        assertEquals("documents/7/original", uploads.fileDirectory());
        assertEquals(MediaType.PDF, uploads.request().mediaType());
        assertSame(media, savedDocument.get().getOriginalMediaAsset());
        assertEquals(ProcessingState.PENDING, savedDocument.get().getProcessingState());
        assertSame(savedDocument.get(), queuedDocument.get());
        assertSame(media, queuedMedia.get());
    }

    @Test
    void uploadsStandaloneCaptureWithOneStablePageRenditionAndOcrJob() {
        AtomicReference<Document> savedDocument = new AtomicReference<>();
        AtomicReference<DocumentPage> savedPage = new AtomicReference<>();
        AtomicReference<DocumentPageMedia> savedRendition = new AtomicReference<>();
        AtomicReference<Object> provenanceEvent = new AtomicReference<>();
        DocumentRepository documents = documentRepository(savedDocument, 8L);
        DocumentPageRepository pages = pageRepository(savedPage, 18L, null);
        DocumentPageMediaRepository pageMedia = pageMediaRepository(savedRendition, 28L);
        MediaAsset media = mediaAsset(38L);
        CapturingUploadService uploads = new CapturingUploadService(mediaDetails(38L, MediaType.IMAGE));
        AtomicReference<DocumentPage> queuedPage = new AtomicReference<>();
        AtomicReference<MediaAsset> queuedMedia = new AtomicReference<>();

        StandaloneCaptureUploadService service = new StandaloneCaptureUploadService(
                documents,
                pages,
                pageMedia,
                mediaRepository(media),
                uploads,
                new NoOpUploadValidator(),
                references(),
                new DocumentCreationFactory(),
                new DocumentPageFactory(),
                new DocumentPageMediaFactory(),
                provenanceRecorder(provenanceEvent),
                jobs(48L, null, queuedPage, queuedMedia),
                new DocumentUploadStatePolicy(),
                null
        );

        DocumentUploadDetails result = service.upload(
                new StandaloneCaptureUploadCommand(
                        metadata("Standalone capture"),
                        unknownProvenance(),
                        "12",
                        12,
                        "Page 12",
                        true,
                        "Original photograph"
                ),
                file("capture.png", "image/png")
        );

        assertEquals(8L, result.documentId());
        assertEquals(18L, result.documentPageId());
        assertEquals(28L, result.documentPageMediaId());
        assertEquals(38L, result.mediaAssetId());
        assertEquals(48L, result.processingJobId());
        assertEquals("captures/8/original", uploads.fileDirectory());
        assertEquals(1, savedDocument.get().getPageCount());
        assertSame(media, savedDocument.get().getOriginalMediaAsset());
        assertEquals(1, savedPage.get().getPageSequence());
        assertEquals(ProcessingState.PENDING, savedPage.get().getProcessingState());
        assertTrue(savedRendition.get().isOriginal());
        assertTrue(savedRendition.get().isPreferredOcrInput());
        assertNotNull(provenanceEvent.get());
        assertSame(savedPage.get(), queuedPage.get());
        assertSame(media, queuedMedia.get());
    }

    @Test
    void addsMissingPageWithoutReplacingExistingPages() {
        Document document = entity(new Document(), 9L);
        AtomicReference<DocumentPage> savedPage = new AtomicReference<>();
        AtomicReference<DocumentPageMedia> savedRendition = new AtomicReference<>();
        AtomicReference<Object> provenanceEvent = new AtomicReference<>();
        AtomicReference<Document> savedDocument = new AtomicReference<>();
        DocumentRepository documents = proxy(
                DocumentRepository.class,
                (ignored, method, arguments) -> switch (method.getName()) {
                    case "findById" -> Optional.of(document);
                    case "save" -> {
                        savedDocument.set((Document) arguments[0]);
                        yield arguments[0];
                    }
                    default -> throw new AssertionError("Unexpected document call: " + method.getName());
                }
        );
        DocumentPageRepository pages = pageRepository(savedPage, 19L, 4L);
        DocumentPageMediaRepository pageMedia = pageMediaRepository(savedRendition, 29L);
        MediaAsset media = mediaAsset(39L);
        CapturingUploadService uploads = new CapturingUploadService(mediaDetails(39L, MediaType.IMAGE));

        MissingPageUploadService service = new MissingPageUploadService(
                documents,
                pages,
                pageMedia,
                mediaRepository(media),
                uploads,
                new NoOpUploadValidator(),
                references(),
                new DocumentPageFactory(),
                new DocumentPageMediaFactory(),
                provenanceRecorder(provenanceEvent),
                noOcrJobs(),
                new DocumentUploadStatePolicy()
        );

        DocumentUploadDetails result = service.upload(
                9L,
                new MissingPageUploadCommand(
                        3,
                        unknownProvenance(),
                        "3",
                        3,
                        "Recovered page",
                        false,
                        "Missing page scan",
                        "Added later"
                ),
                file("page.png", "image/png")
        );

        assertEquals(9L, result.documentId());
        assertEquals(19L, result.documentPageId());
        assertEquals(29L, result.documentPageMediaId());
        assertNull(result.processingJobId());
        assertEquals("documents/9/pages", uploads.fileDirectory());
        assertEquals(PageRole.MISSING_PAGE, savedPage.get().getPageRole());
        assertEquals(3, savedPage.get().getPageSequence());
        assertEquals(4, savedDocument.get().getPageCount());
        assertEquals(ProcessingState.UPLOADED, savedDocument.get().getProcessingState());
        assertTrue(savedRendition.get().isOriginal());
        assertNotNull(provenanceEvent.get());
    }

    private DocumentRepository documentRepository(AtomicReference<Document> saved, Long id) {
        return proxy(
                DocumentRepository.class,
                (ignored, method, arguments) -> switch (method.getName()) {
                    case "saveAndFlush" -> {
                        Document document = (Document) arguments[0];
                        EntityTestUtils.setId(document, id);
                        saved.set(document);
                        yield document;
                    }
                    case "save" -> {
                        saved.set((Document) arguments[0]);
                        yield arguments[0];
                    }
                    default -> throw new AssertionError("Unexpected document call: " + method.getName());
                }
        );
    }

    private DocumentPageRepository pageRepository(
            AtomicReference<DocumentPage> saved,
            Long pageId,
            Long pageCount
    ) {
        return proxy(
                DocumentPageRepository.class,
                (ignored, method, arguments) -> switch (method.getName()) {
                    case "findByDocument_IdAndPageSequence" -> Optional.empty();
                    case "saveAndFlush" -> {
                        DocumentPage page = (DocumentPage) arguments[0];
                        EntityTestUtils.setId(page, pageId);
                        saved.set(page);
                        yield page;
                    }
                    case "countByDocument_Id" -> pageCount;
                    default -> throw new AssertionError("Unexpected page call: " + method.getName());
                }
        );
    }

    private DocumentPageMediaRepository pageMediaRepository(
            AtomicReference<DocumentPageMedia> saved,
            Long renditionId
    ) {
        return proxy(
                DocumentPageMediaRepository.class,
                (ignored, method, arguments) -> {
                    if (method.getName().equals("saveAndFlush")) {
                        DocumentPageMedia rendition = (DocumentPageMedia) arguments[0];
                        EntityTestUtils.setId(rendition, renditionId);
                        saved.set(rendition);
                        return rendition;
                    }
                    throw new AssertionError("Unexpected rendition call: " + method.getName());
                }
        );
    }

    private MediaAssetRepository mediaRepository(MediaAsset media) {
        return proxy(
                MediaAssetRepository.class,
                (ignored, method, arguments) -> {
                    if (method.getName().equals("getReferenceById"))
                        return media;
                    throw new AssertionError("Unexpected media call: " + method.getName());
                }
        );
    }

    private DocumentPageProvenanceRecorder provenanceRecorder(AtomicReference<Object> saved) {
        DocumentPageProvenanceEventRepository repository = proxy(
                DocumentPageProvenanceEventRepository.class,
                (ignored, method, arguments) -> {
                    if (method.getName().equals("save")) {
                        saved.set(arguments[0]);
                        return arguments[0];
                    }
                    throw new AssertionError("Unexpected provenance call: " + method.getName());
                }
        );
        return new DocumentPageProvenanceRecorder(repository);
    }

    private DocumentProcessingJobScheduler jobs(
            Long jobId,
            AtomicReference<Document> queuedDocument,
            AtomicReference<DocumentPage> queuedPage,
            AtomicReference<MediaAsset> queuedMedia
    ) {
        return new DocumentProcessingJobScheduler(null, null, null) {
            @Override
            public DocumentProcessingJob queuePageExtraction(Document document, MediaAsset originalMedia) {
                if (queuedDocument != null)
                    queuedDocument.set(document);
                queuedMedia.set(originalMedia);
                return job(jobId);
            }

            @Override
            public DocumentProcessingJob queueOcr(DocumentPage page, MediaAsset inputMedia) {
                queuedPage.set(page);
                queuedMedia.set(inputMedia);
                return job(jobId);
            }
        };
    }

    private DocumentProcessingJobScheduler noOcrJobs() {
        return new DocumentProcessingJobScheduler(null, null, null) {
            @Override
            public DocumentProcessingJob queueOcr(DocumentPage page, MediaAsset inputMedia) {
                throw new AssertionError("OCR must not be queued");
            }
        };
    }

    private DocumentProcessingJob job(Long id) {
        return entity(new DocumentProcessingJob(), id);
    }

    private DocumentUploadReferenceResolver references() {
        return new DocumentUploadReferenceResolver(
                rejecting(SourceRepository.class),
                rejecting(SourceReferenceRepository.class)
        );
    }

    private DocumentBibliographicInput metadata(String title) {
        return new DocumentBibliographicInput(
                null,
                null,
                title,
                null,
                null,
                null,
                "bg",
                null
        );
    }

    private PageProvenanceInput unknownProvenance() {
        return new PageProvenanceInput(
                null,
                ProvenanceStatus.UNKNOWN_SOURCE,
                ProvenanceTrustState.UNKNOWN,
                "Source is not yet identified",
                "curator",
                "Initial upload"
        );
    }

    private MediaAsset mediaAsset(Long id) {
        MediaAsset media = entity(new MediaAsset(), id);
        media.setWidth(1200);
        media.setHeight(800);
        media.setChecksum("hash-" + id);
        return media;
    }

    private MediaAssetDetails mediaDetails(Long id, MediaType type) {
        String mime = type == MediaType.PDF ? "application/pdf" : "image/png";
        return new MediaAssetDetails(
                id,
                null,
                "upload",
                "server/owned/path",
                null,
                mime,
                type,
                type == MediaType.PDF ? null : 1200,
                type == MediaType.PDF ? null : 800,
                10L,
                "hash-" + id,
                null,
                null,
                null,
                null,
                null
        );
    }

    private MockMultipartFile file(String name, String mime) {
        return new MockMultipartFile("file", name, mime, new byte[]{1});
    }

    private <T extends fmi.ethnowear.persistence.jpa.entity.AppendOnlyEntity> T entity(T entity, Long id) {
        EntityTestUtils.setId(entity, id);
        return entity;
    }

    private static final class CapturingUploadService extends MediaUploadService {

        private final MediaAssetDetails result;
        private MediaUploadRequest request;
        private MediaUploadDestination destination;

        private CapturingUploadService(MediaAssetDetails result) {
            super(null, null, null, null, null, null, null, null);
            this.result = result;
        }

        @Override
        public MediaAssetDetails upload(
                MultipartFile file,
                MediaUploadRequest request,
                MediaUploadDestination destination
        ) {
            this.request = request;
            this.destination = destination;
            return result;
        }

        private MediaUploadRequest request() {
            return request;
        }

        private String fileDirectory() {
            return (String) ReflectionTestUtils.getField(destination, "fileDirectory");
        }
    }

    private static final class NoOpUploadValidator extends DocumentUploadValidator {

        private NoOpUploadValidator() {
            super(null);
        }

        @Override
        public void validate(PdfDocumentUploadCommand command) {
        }

        @Override
        public void validate(StandaloneCaptureUploadCommand command) {
        }

        @Override
        public void validate(MissingPageUploadCommand command) {
        }
    }
}
