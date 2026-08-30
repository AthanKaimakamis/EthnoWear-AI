package fmi.ethnowear.application.service.archive.media;

import fmi.ethnowear.application.service.archive.media.asset.MediaAssetService;
import fmi.ethnowear.application.service.archive.media.asset.MediaAssetUsageChecker;
import fmi.ethnowear.application.service.archive.media.attachment.ArchiveItemMediaService;
import fmi.ethnowear.application.service.archive.media.attachment.ArchiveItemMediaUsageChecker;
import fmi.ethnowear.application.service.archive.media.attachment.MediaEntityLinkService;
import fmi.ethnowear.application.service.archive.media.asset.MediaAssetMapper;
import fmi.ethnowear.application.service.archive.media.attachment.ArchiveItemMediaMapper;
import fmi.ethnowear.testutil.EntityTestUtils;

import fmi.ethnowear.application.dto.archive.media.ArchiveItemMediaDetails;
import fmi.ethnowear.application.dto.archive.media.ArchiveItemMediaWriteDto;
import fmi.ethnowear.application.dto.archive.media.MediaAssetMetadataWriteDto;
import fmi.ethnowear.application.dto.archive.media.MediaEntityLinkDetails;
import fmi.ethnowear.application.dto.archive.media.MediaEntityLinkWriteDto;
import fmi.ethnowear.domain.model.archive.MediaRole;
import fmi.ethnowear.domain.model.archive.MediaType;
import fmi.ethnowear.domain.model.media.MediaOrigin;
import fmi.ethnowear.domain.model.document.figure.FigureReviewState;
import fmi.ethnowear.domain.model.ontology.FeatureType;
import fmi.ethnowear.application.exception.ResourceInUseException;
import fmi.ethnowear.application.exception.ArchiveItemNotEditableException;
import fmi.ethnowear.domain.model.archive.PublicationStatus;
import fmi.ethnowear.application.service.archive.workflow.ArchiveItemWorkflowGuard;
import fmi.ethnowear.persistence.jpa.entity.ArchiveItem;
import fmi.ethnowear.persistence.jpa.entity.ArchiveItemMedia;
import fmi.ethnowear.persistence.jpa.entity.MediaAsset;
import fmi.ethnowear.persistence.jpa.entity.MediaEntityLink;
import fmi.ethnowear.persistence.jpa.repository.ArchiveItemMediaRepository;
import fmi.ethnowear.persistence.jpa.repository.ArchiveItemRepository;
import fmi.ethnowear.persistence.jpa.repository.MediaAssetRepository;
import fmi.ethnowear.persistence.jpa.repository.MediaFeatureAnnotationRepository;
import fmi.ethnowear.persistence.jpa.repository.MediaEntityLinkRepository;
import fmi.ethnowear.persistence.jpa.repository.SourceReferenceRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageFigureRepository;
import fmi.ethnowear.persistence.jpa.entity.SourceReference;
import fmi.ethnowear.persistence.jpa.entity.document.Document;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPage;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPageFigure;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.Optional;
import java.util.List;

import static fmi.ethnowear.support.RepositoryTestProxies.proxy;
import static fmi.ethnowear.support.RepositoryTestProxies.rejecting;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MediaServicesTest {

    @Test
    void excludesGeneratedMediaFromGeneralLibrary() {
        MediaAsset visible = new MediaAsset();
        EntityTestUtils.setId(visible, 3L);
        var pageable = PageRequest.of(0, 20);
        var expected = new PageImpl<>(java.util.List.of(visible), pageable, 1);

        MediaAssetRepository repository = proxy(
                MediaAssetRepository.class,
                (ignored, method, arguments) -> {
                    assertEquals("findVisibleInGeneralLibrary", method.getName());
                    assertEquals(MediaOrigin.GENERATED, arguments[0]);
                    assertEquals(FigureReviewState.APPROVED, arguments[1]);
                    assertSame(pageable, arguments[2]);
                    return expected;
                }
        );
        DocumentPageFigureRepository figures = proxy(
                DocumentPageFigureRepository.class,
                (ignored, method, arguments) -> List.of()
        );
        MediaAssetService service = new MediaAssetService(
                repository,
                figures,
                null,
                new MediaAssetMapper(),
                null,
                null
        );

        var result = service.findAll(pageable);

        assertEquals(1, result.getTotalElements());
        assertEquals(3L, result.getContent().getFirst().id());
    }

    @Test
    void exposesApprovedFigureLinkWithoutAdditionalPerAssetQueries() {
        MediaAsset asset = new MediaAsset();
        EntityTestUtils.setId(asset, 30L);
        Document document = new Document();
        EntityTestUtils.setId(document, 10L);
        DocumentPage page = new DocumentPage();
        EntityTestUtils.setId(page, 20L);
        page.setDocument(document);
        page.setPageSequence(4);
        SourceReference reference = new SourceReference();
        EntityTestUtils.setId(reference, 40L);
        DocumentPageFigure figure = new DocumentPageFigure();
        EntityTestUtils.setId(figure, 50L);
        figure.setDocumentPage(page);
        figure.setMediaAsset(asset);
        figure.setSourceReference(reference);
        figure.setCorrectedCaptionText("Одобрена фигура");
        figure.setPrintedFigureNumber("Фиг. 12");
        figure.setReviewState(FigureReviewState.APPROVED);

        var pageable = PageRequest.of(0, 20);
        MediaAssetRepository repository = proxy(
                MediaAssetRepository.class,
                (ignored, method, arguments) -> new PageImpl<>(
                        List.of(asset),
                        pageable,
                        1
                )
        );
        DocumentPageFigureRepository figures = proxy(
                DocumentPageFigureRepository.class,
                (ignored, method, arguments) -> {
                    assertEquals(
                            "findByMediaAsset_IdInAndReviewState",
                            method.getName()
                    );
                    return List.of(figure);
                }
        );
        MediaAssetService service = new MediaAssetService(
                repository,
                figures,
                null,
                new MediaAssetMapper(),
                null,
                null
        );

        var link = service.findAll(pageable)
                .getContent()
                .getFirst()
                .documentFigure();

        assertEquals(10L, link.documentId());
        assertEquals(20L, link.documentPageId());
        assertEquals(4, link.pageSequence());
        assertEquals(50L, link.figureId());
        assertEquals("Одобрена фигура", link.caption());
        assertEquals(40L, link.sourceReferenceId());
        assertEquals(FigureReviewState.APPROVED, link.reviewState());
    }

    @Test
    void updatesCuratorMetadataWithoutChangingSystemManagedFields() {
        MediaAsset asset = new MediaAsset();
        EntityTestUtils.setId(asset, 4L);
        asset.setFileName("original.jpg");
        asset.setFilePath("archive/original.jpg");
        asset.setMimeType("image/jpeg");
        asset.setMediaType(MediaType.IMAGE);
        asset.setWidth(1200);
        asset.setHeight(800);
        asset.setSizeBytes(250000L);
        asset.setChecksum("original-checksum");
        asset.setThumbnailPath("thumbnails/original.jpg");

        var sourceReference = new fmi.ethnowear.persistence.jpa.entity.SourceReference();
        EntityTestUtils.setId(sourceReference, 9L);
        MediaAssetRepository assetRepository = proxy(
                MediaAssetRepository.class,
                (ignored, method, arguments) -> switch(method.getName()) {
                    case "findById" -> Optional.of(asset);
                    case "save" -> arguments[0];
                    default -> throw new AssertionError("Unexpected repository call: " + method.getName());
                }
        );
        SourceReferenceRepository sourceReferenceRepository = proxy(
                SourceReferenceRepository.class,
                (ignored, method, arguments) -> Optional.of(sourceReference)
        );
        MediaAssetService service = new MediaAssetService(
                assetRepository,
                noApprovedFigures(),
                sourceReferenceRepository,
                new MediaAssetMapper(),
                new MediaAssetUsageChecker(null),
                null
        );

        var result = service.updateMetadata(
                4L,
                new MediaAssetMetadataWriteDto(9L, "Curator description")
        );

        assertEquals(9L, result.sourceReferenceId());
        assertEquals("Curator description", result.description());
        assertEquals("archive/original.jpg", result.filePath());
        assertEquals("image/jpeg", result.mimeType());
        assertEquals(1200, result.width());
        assertEquals(800, result.height());
        assertEquals(250000L, result.sizeBytes());
        assertEquals("original-checksum", result.checksum());
        assertEquals("thumbnails/original.jpg", result.thumbnailPath());
    }

    @Test
    void blocksDeletionOfAttachedMediaAsset() {
        MediaAsset asset = new MediaAsset();
        EntityTestUtils.setId(asset, 5L);
        MediaAssetRepository assetRepository = proxy(MediaAssetRepository.class, (ignored, method, arguments) -> {
            if(method.getName().equals("findById"))
                return Optional.of(asset);

            throw new AssertionError("Unexpected repository call: " + method.getName());
        });
        ArchiveItemMediaRepository itemMediaRepository = proxy(
                ArchiveItemMediaRepository.class,
                (ignored, method, arguments) -> true
        );
        MediaAssetService service = new MediaAssetService(
                assetRepository,
                null,
                null,
                new MediaAssetMapper(),
                new MediaAssetUsageChecker(itemMediaRepository),
                null
        );

        assertThrows(ResourceInUseException.class, () -> service.delete(5L));
    }

    private DocumentPageFigureRepository noApprovedFigures() {
        return proxy(
                DocumentPageFigureRepository.class,
                (ignored, method, arguments) -> Optional.empty()
        );
    }

    @Test
    void createsArchiveItemMediaForExistingResources() {
        ArchiveItem item = new ArchiveItem();
        EntityTestUtils.setId(item, 6L);
        MediaAsset asset = new MediaAsset();
        EntityTestUtils.setId(asset, 7L);
        ArchiveItemRepository itemRepository = proxy(
                ArchiveItemRepository.class,
                (ignored, method, arguments) -> Optional.of(item)
        );
        MediaAssetRepository assetRepository = proxy(
                MediaAssetRepository.class,
                (ignored, method, arguments) -> Optional.of(asset)
        );
        ArchiveItemMediaRepository itemMediaRepository = proxy(
                ArchiveItemMediaRepository.class,
                (ignored, method, arguments) -> (ArchiveItemMedia) arguments[0]
        );
        ArchiveItemMediaService service = new ArchiveItemMediaService(
                itemMediaRepository,
                itemRepository,
                assetRepository,
                new ArchiveItemMediaMapper(),
                new ArchiveItemMediaUsageChecker(null),
                new ArchiveItemWorkflowGuard()
        );

        ArchiveItemMediaDetails details = service.create(
                new ArchiveItemMediaWriteDto(6L, 7L, MediaRole.PRIMARY, null, "Primary image")
        );

        assertEquals(6L, details.archiveItemId());
        assertEquals(7L, details.mediaAssetId());
        assertEquals(MediaRole.PRIMARY, details.role());
    }

    @Test
    void rejectsAddingMediaToPublishedArchiveItem() {
        ArchiveItem item = new ArchiveItem();
        EntityTestUtils.setId(item, 6L);
        item.setPublicationStatus(PublicationStatus.PUBLISHED);
        ArchiveItemRepository itemRepository = proxy(
                ArchiveItemRepository.class,
                (ignored, method, arguments) -> Optional.of(item)
        );
        ArchiveItemMediaService service = new ArchiveItemMediaService(
                rejecting(ArchiveItemMediaRepository.class),
                itemRepository,
                rejecting(MediaAssetRepository.class),
                new ArchiveItemMediaMapper(),
                new ArchiveItemMediaUsageChecker(null),
                new ArchiveItemWorkflowGuard()
        );

        assertThrows(
                ArchiveItemNotEditableException.class,
                () -> service.create(new ArchiveItemMediaWriteDto(
                        6L,
                        7L,
                        MediaRole.PRIMARY,
                        null,
                        null
                ))
        );
    }

    @Test
    void blocksDeletionOfAnnotatedArchiveItemMedia() {
        ArchiveItem item = new ArchiveItem();
        ArchiveItemMedia itemMedia = new ArchiveItemMedia();
        EntityTestUtils.setId(itemMedia, 8L);
        itemMedia.setArchiveItem(item);
        ArchiveItemMediaRepository itemMediaRepository = proxy(
                ArchiveItemMediaRepository.class,
                (ignored, method, arguments) -> Optional.of(itemMedia)
        );
        MediaFeatureAnnotationRepository annotationRepository = proxy(
                MediaFeatureAnnotationRepository.class,
                (ignored, method, arguments) -> true
        );
        ArchiveItemMediaService service = new ArchiveItemMediaService(
                itemMediaRepository,
                null,
                null,
                new ArchiveItemMediaMapper(),
                new ArchiveItemMediaUsageChecker(annotationRepository),
                new ArchiveItemWorkflowGuard()
        );

        assertThrows(ResourceInUseException.class, () -> service.delete(8L));
    }

    @Test
    void createsMediaLinksForEveryNavigableOntologyEntityType() {
        MediaAsset asset = new MediaAsset();
        EntityTestUtils.setId(asset, 12L);
        MediaAssetRepository assetRepository = proxy(
                MediaAssetRepository.class,
                (ignored, method, arguments) -> Optional.of(asset)
        );
        MediaEntityLinkRepository linkRepository = proxy(
                MediaEntityLinkRepository.class,
                (ignored, method, arguments) -> (MediaEntityLink) arguments[0]
        );
        MediaEntityLinkService service = new MediaEntityLinkService(linkRepository, assetRepository);

        for (FeatureType entityType : FeatureType.values()) {
            MediaEntityLinkDetails details = service.create(new MediaEntityLinkWriteDto(
                    12L,
                    entityType,
                    "http://example.org/ontology#Example",
                    "Example",
                    null
            ));

            assertEquals(entityType, details.entityType());
            assertEquals(12L, details.mediaAssetId());
        }
    }

    @Test
    void rejectsMediaLinkWithoutCompleteOntologyIdentity() {
        MediaEntityLinkService service = new MediaEntityLinkService(
                rejecting(MediaEntityLinkRepository.class),
                rejecting(MediaAssetRepository.class)
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.create(new MediaEntityLinkWriteDto(
                        12L,
                        FeatureType.TECHNIQUE,
                        "http://example.org/ontology#ChainStitch",
                        null,
                        null
                ))
        );

        assertEquals(
                "Ontology IRI and local name are required",
                exception.getMessage()
        );
    }
}
