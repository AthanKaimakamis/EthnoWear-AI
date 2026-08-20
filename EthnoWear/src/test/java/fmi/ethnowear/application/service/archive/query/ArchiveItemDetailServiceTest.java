package fmi.ethnowear.application.service.archive.query;

import fmi.ethnowear.testutil.EntityTestUtils;

import fmi.ethnowear.application.dto.archive.query.ArchiveItemDetailDetails;
import fmi.ethnowear.domain.model.archive.MediaFeatureAnnotationType;
import fmi.ethnowear.domain.model.archive.SourceType;
import fmi.ethnowear.application.exception.ResourceNotFoundException;
import fmi.ethnowear.application.service.archive.item.ArchiveItemFeatureMapper;
import fmi.ethnowear.application.service.archive.item.ArchiveItemMapper;
import fmi.ethnowear.application.service.archive.media.asset.MediaAssetMapper;
import fmi.ethnowear.application.service.archive.media.attachment.ArchiveItemMediaMapper;
import fmi.ethnowear.application.service.archive.media.attachment.MediaFeatureAnnotationMapper;
import fmi.ethnowear.persistence.jpa.entity.ArchiveItem;
import fmi.ethnowear.persistence.jpa.entity.ArchiveItemFeature;
import fmi.ethnowear.persistence.jpa.entity.ArchiveItemMedia;
import fmi.ethnowear.persistence.jpa.entity.MediaAsset;
import fmi.ethnowear.persistence.jpa.entity.MediaFeatureAnnotation;
import fmi.ethnowear.persistence.jpa.entity.Source;
import fmi.ethnowear.persistence.jpa.entity.SourceReference;
import fmi.ethnowear.persistence.jpa.repository.ArchiveItemFeatureRepository;
import fmi.ethnowear.persistence.jpa.repository.ArchiveItemMediaRepository;
import fmi.ethnowear.persistence.jpa.repository.ArchiveItemRepository;
import fmi.ethnowear.persistence.jpa.repository.MediaFeatureAnnotationRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static fmi.ethnowear.support.RepositoryTestProxies.proxy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ArchiveItemDetailServiceTest {

    @Test
    void buildsDetailWithOrderedFeaturesAndMediaAndGroupedAnnotations() {
        ArchiveItem item = archiveItem(7L);
        ArchiveItemFeature laterFeature = feature(22L, item);
        ArchiveItemFeature earlierFeature = feature(11L, item);
        ArchiveItemMedia laterMedia = media(44L, item);
        ArchiveItemMedia earlierMedia = media(33L, item);
        MediaFeatureAnnotation annotation = annotation(55L, earlierMedia, earlierFeature);

        ArchiveItemDetailService service = service(
                7L,
                Optional.of(item),
                List.of(laterFeature, earlierFeature),
                List.of(laterMedia, earlierMedia),
                List.of(annotation)
        );

        ArchiveItemDetailDetails result = service.findById(7L);

        assertEquals(7L, result.archiveItem().id());
        assertEquals(item.getSourceReference().getId(), result.source().sourceReferenceId());
        assertEquals(List.of(11L, 22L), result.features().stream().map(feature -> feature.id()).toList());
        assertEquals(List.of(33L, 44L), result.media().stream().map(media -> media.media().id()).toList());
        assertEquals(List.of(55L), result.media().getFirst().annotations().stream()
                .map(value -> value.id()).toList());
        assertEquals(List.of(), result.media().getLast().annotations());
    }

    @Test
    void throwsWhenArchiveItemDoesNotExist() {
        ArchiveItemDetailService service = service(
                99L,
                Optional.empty(),
                List.of(),
                List.of(),
                List.of()
        );

        assertThrows(ResourceNotFoundException.class, () -> service.findById(99L));
    }

    private ArchiveItemDetailService service(
            Long expectedId,
            Optional<ArchiveItem> item,
            List<ArchiveItemFeature> features,
            List<ArchiveItemMedia> media,
            List<MediaFeatureAnnotation> annotations
    ) {
        ArchiveItemRepository itemRepository = proxy(
                ArchiveItemRepository.class,
                (ignored, method, arguments) -> {
                    assertEquals("findPublishedById", method.getName());
                    assertEquals(expectedId, arguments[0]);
                    return item;
                }
        );
        ArchiveItemFeatureRepository featureRepository = proxy(
                ArchiveItemFeatureRepository.class,
                (ignored, method, arguments) -> features
        );
        ArchiveItemMediaRepository mediaRepository = proxy(
                ArchiveItemMediaRepository.class,
                (ignored, method, arguments) -> media
        );
        MediaFeatureAnnotationRepository annotationRepository = proxy(
                MediaFeatureAnnotationRepository.class,
                (ignored, method, arguments) -> annotations
        );

        return new ArchiveItemDetailService(
                itemRepository,
                featureRepository,
                mediaRepository,
                annotationRepository,
                new ArchiveItemMapper(),
                new ArchiveItemFeatureMapper(),
                new ArchiveItemMediaMapper(),
                new MediaAssetMapper(),
                new MediaFeatureAnnotationMapper(),
                new EntitySourceCitationMapper()
        );
    }

    private ArchiveItem archiveItem(Long id) {
        Source source = new Source();
        EntityTestUtils.setId(source, 80L);
        source.setTitle("Archive source");
        source.setSourceType(SourceType.BOOK);

        SourceReference reference = new SourceReference();
        EntityTestUtils.setId(reference, 70L);
        reference.setSource(source);

        ArchiveItem item = new ArchiveItem();
        EntityTestUtils.setId(item, id);
        item.setSourceReference(reference);
        return item;
    }

    private ArchiveItemFeature feature(Long id, ArchiveItem item) {
        ArchiveItemFeature feature = new ArchiveItemFeature();
        EntityTestUtils.setId(feature, id);
        feature.setArchiveItem(item);
        return feature;
    }

    private ArchiveItemMedia media(Long id, ArchiveItem item) {
        MediaAsset asset = new MediaAsset();
        EntityTestUtils.setId(asset, id + 100);

        ArchiveItemMedia media = new ArchiveItemMedia();
        EntityTestUtils.setId(media, id);
        media.setArchiveItem(item);
        media.setMediaAsset(asset);
        return media;
    }

    private MediaFeatureAnnotation annotation(
            Long id,
            ArchiveItemMedia media,
            ArchiveItemFeature feature
    ) {
        MediaFeatureAnnotation annotation = new MediaFeatureAnnotation();
        EntityTestUtils.setId(annotation, id);
        annotation.setArchiveItemMedia(media);
        annotation.setArchiveItemFeature(feature);
        annotation.setAnnotationType(MediaFeatureAnnotationType.VISIBLE_IN_IMAGE);
        return annotation;
    }
}
