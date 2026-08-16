package fmi.ethnowear.application.service.archive.media;

import fmi.ethnowear.application.dto.archive.media.ArchiveItemMediaDetails;
import fmi.ethnowear.application.dto.archive.media.ArchiveItemMediaWriteDto;
import fmi.ethnowear.application.dto.archive.media.MediaAssetWriteDto;
import fmi.ethnowear.application.dto.archive.media.MediaEntityLinkDetails;
import fmi.ethnowear.application.dto.archive.media.MediaEntityLinkWriteDto;
import fmi.ethnowear.domain.model.archive.MediaRole;
import fmi.ethnowear.domain.model.archive.MediaType;
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
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static fmi.ethnowear.support.RepositoryTestProxies.proxy;
import static fmi.ethnowear.support.RepositoryTestProxies.rejecting;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MediaServicesTest {

    @Test
    void rejectsMediaAssetWithoutLocation() {
        MediaAssetService service = new MediaAssetService(
                rejecting(MediaAssetRepository.class),
                rejecting(SourceReferenceRepository.class),
                new MediaAssetMapper(),
                new MediaAssetUsageChecker(null),
                null
        );
        MediaAssetWriteDto input = new MediaAssetWriteDto(
                null,
                "test.jpg",
                null,
                null,
                "image/jpeg",
                MediaType.IMAGE,
                100,
                100,
                1000L,
                null
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.create(input)
        );

        assertEquals("A relative managed file path is required", exception.getMessage());
    }

    @Test
    void blocksDeletionOfAttachedMediaAsset() {
        MediaAsset asset = new MediaAsset();
        asset.setId(5L);
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
                new MediaAssetMapper(),
                new MediaAssetUsageChecker(itemMediaRepository),
                null
        );

        assertThrows(ResourceInUseException.class, () -> service.delete(5L));
    }

    @Test
    void createsArchiveItemMediaForExistingResources() {
        ArchiveItem item = new ArchiveItem();
        item.setId(6L);
        MediaAsset asset = new MediaAsset();
        asset.setId(7L);
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
        item.setId(6L);
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
        itemMedia.setId(8L);
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
        asset.setId(12L);
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
