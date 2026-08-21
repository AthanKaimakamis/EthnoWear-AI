package fmi.ethnowear.application.service.document.upload;

import fmi.ethnowear.application.dto.archive.media.MediaAssetDetails;
import fmi.ethnowear.application.dto.archive.media.MediaUploadRequest;
import fmi.ethnowear.application.dto.document.command.upload.ReplacementRenditionUploadCommand;
import fmi.ethnowear.application.service.archive.media.storage.MediaUploadDestination;
import fmi.ethnowear.application.service.archive.media.storage.MediaUploadService;
import fmi.ethnowear.application.service.document.processing.DocumentProcessingJobScheduler;
import fmi.ethnowear.domain.model.archive.MediaType;
import fmi.ethnowear.persistence.jpa.entity.MediaAsset;
import fmi.ethnowear.persistence.jpa.entity.document.Document;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPage;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPageMedia;
import fmi.ethnowear.persistence.jpa.repository.MediaAssetRepository;
import fmi.ethnowear.persistence.jpa.repository.SourceReferenceRepository;
import fmi.ethnowear.persistence.jpa.repository.SourceRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageMediaRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentRepository;
import fmi.ethnowear.testutil.EntityTestUtils;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static fmi.ethnowear.support.RepositoryTestProxies.proxy;
import static fmi.ethnowear.support.RepositoryTestProxies.rejecting;
import static org.junit.jupiter.api.Assertions.*;

class ReplacementRenditionUploadServiceTest {

    @Test
    void clearsAndFlushesOldPreferredInputBeforeSavingReplacement() {
        Document document = new Document();
        EntityTestUtils.setId(document, 7L);
        DocumentPage page = new DocumentPage();
        EntityTestUtils.setId(page, 11L);
        page.setDocument(document);

        DocumentPageMedia original = new DocumentPageMedia();
        original.setDocumentPage(page);
        original.setPreferredOcrInput(true);
        original.setDisplayOrder(3);

        MediaAsset replacementAsset = new MediaAsset();
        EntityTestUtils.setId(replacementAsset, 19L);
        replacementAsset.setWidth(1200);
        replacementAsset.setHeight(800);
        replacementAsset.setChecksum("replacement-hash");

        List<String> calls = new ArrayList<>();
        DocumentPageMediaRepository pageMedia = proxy(
                DocumentPageMediaRepository.class,
                (ignored, method, arguments) -> {
                    calls.add(method.getName());
                    return switch (method.getName()) {
                        case "findByDocumentPage_IdOrderByDisplayOrderAscIdAsc" -> List.of(original);
                        case "saveAll" -> arguments[0];
                        case "flush" -> null;
                        case "saveAndFlush" -> {
                            DocumentPageMedia saved = (DocumentPageMedia) arguments[0];
                            EntityTestUtils.setId(saved, 23L);
                            yield saved;
                        }
                        default -> throw new AssertionError("Unexpected page-media call: " + method.getName());
                    };
                }
        );

        DocumentPageRepository pages = proxy(
                DocumentPageRepository.class,
                (ignored, method, arguments) -> switch (method.getName()) {
                    case "findByIdAndDocument_Id" -> Optional.of(page);
                    case "save" -> arguments[0];
                    default -> throw new AssertionError("Unexpected page call: " + method.getName());
                }
        );

        MediaAssetRepository mediaAssets = proxy(
                MediaAssetRepository.class,
                (ignored, method, arguments) -> {
                    if (method.getName().equals("getReferenceById"))
                        return replacementAsset;
                    throw new AssertionError("Unexpected media call: " + method.getName());
                }
        );

        DocumentRepository documents = proxy(
                DocumentRepository.class,
                (ignored, method, arguments) -> {
                    if (method.getName().equals("save"))
                        return arguments[0];
                    throw new AssertionError("Unexpected document call: " + method.getName());
                }
        );

        MediaUploadService uploads = new MediaUploadService(null, null, null, null, null, null, null, null) {
            @Override
            public MediaAssetDetails upload(
                    MultipartFile file,
                    MediaUploadRequest request,
                    MediaUploadDestination destination
            ) {
                return mediaDetails(19L);
            }
        };

        DocumentUploadValidator validator = new DocumentUploadValidator(null) {
            @Override
            public void validate(ReplacementRenditionUploadCommand command) {
            }
        };

        DocumentProcessingJobScheduler jobs = new DocumentProcessingJobScheduler(null, null) {
            @Override
            public fmi.ethnowear.persistence.jpa.entity.document.DocumentProcessingJob queueOcr(
                    DocumentPage documentPage,
                    MediaAsset inputMedia
            ) {
                throw new AssertionError("OCR must not be queued");
            }
        };

        DocumentUploadReferenceResolver references = new DocumentUploadReferenceResolver(
                rejecting(SourceRepository.class),
                rejecting(SourceReferenceRepository.class)
        );

        ReplacementRenditionUploadService service = new ReplacementRenditionUploadService(
                documents,
                pages,
                pageMedia,
                mediaAssets,
                uploads,
                validator,
                references,
                new DocumentPageMediaFactory(),
                jobs,
                new DocumentUploadStatePolicy()
        );

        var result = service.upload(
                7L,
                11L,
                new ReplacementRenditionUploadCommand(
                        null,
                        true,
                        false,
                        "Replacement scan",
                        "Sharper capture"
                ),
                new MockMultipartFile("file", "replacement.png", "image/png", new byte[]{1})
        );

        assertFalse(original.isPreferredOcrInput());
        assertEquals(7L, result.documentId());
        assertEquals(11L, result.documentPageId());
        assertEquals(19L, result.mediaAssetId());
        assertEquals(23L, result.documentPageMediaId());
        assertNull(result.processingJobId());
        assertEquals(
                List.of(
                        "findByDocumentPage_IdOrderByDisplayOrderAscIdAsc",
                        "saveAll",
                        "flush",
                        "saveAndFlush"
                ),
                calls
        );
    }

    private MediaAssetDetails mediaDetails(Long id) {
        return new MediaAssetDetails(
                id,
                null,
                "replacement.png",
                "documents/7/pages/replacement.png",
                null,
                "image/png",
                MediaType.SCAN,
                1200,
                800,
                10L,
                "replacement-hash",
                null,
                "Replacement scan",
                null,
                null
        );
    }
}
