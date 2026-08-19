package fmi.ethnowear.application.service.archive.media;

import fmi.ethnowear.testutil.EntityTestUtils;

import fmi.ethnowear.application.dto.archive.media.MediaFeatureAnnotationDetails;
import fmi.ethnowear.application.dto.archive.media.MediaFeatureAnnotationWriteDto;
import fmi.ethnowear.domain.model.archive.MediaFeatureAnnotationType;
import fmi.ethnowear.persistence.jpa.entity.ArchiveItem;
import fmi.ethnowear.persistence.jpa.entity.ArchiveItemFeature;
import fmi.ethnowear.persistence.jpa.entity.ArchiveItemMedia;
import fmi.ethnowear.persistence.jpa.entity.MediaFeatureAnnotation;
import fmi.ethnowear.persistence.jpa.repository.ArchiveItemFeatureRepository;
import fmi.ethnowear.persistence.jpa.repository.ArchiveItemMediaRepository;
import fmi.ethnowear.persistence.jpa.repository.MediaFeatureAnnotationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MediaFeatureAnnotationServiceTest {

    private MediaFeatureAnnotationRepository annotationRepository;
    private ArchiveItemMediaRepository itemMediaRepository;
    private ArchiveItemFeatureRepository itemFeatureRepository;
    private MediaFeatureAnnotationService service;

    @BeforeEach
    void setUp() {
        annotationRepository = rejectingRepository(MediaFeatureAnnotationRepository.class);
        itemMediaRepository = rejectingRepository(ArchiveItemMediaRepository.class);
        itemFeatureRepository = rejectingRepository(ArchiveItemFeatureRepository.class);
        createService();
    }

    private void createService() {
        service = new MediaFeatureAnnotationService(
                annotationRepository,
                itemMediaRepository,
                itemFeatureRepository,
                new MediaFeatureAnnotationMapper()
        );
    }

    @Test
    void rejectsCoordinatesWithUnsupportedScale() {
        MediaFeatureAnnotationWriteDto input = input(
                "0.100000",
                "0.100000",
                "0.0000001",
                "0.200000"
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.create(input)
        );

        assertEquals("Annotation coordinates cannot have more than 6 decimal places", exception.getMessage());
    }

    @Test
    void rejectsRectangleOutsideMediaBounds() {
        MediaFeatureAnnotationWriteDto input = input(
                "0.800000",
                "0.100000",
                "0.300000",
                "0.200000"
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.create(input)
        );

        assertEquals("Annotation rectangle must fit within the media bounds", exception.getMessage());
    }

    @Test
    void rejectsMediaAndFeatureFromDifferentArchiveItems() {
        ArchiveItemMedia itemMedia = itemMedia(1L, 10L);
        ArchiveItemFeature itemFeature = itemFeature(2L, 20L);
        itemMediaRepository = repository(ArchiveItemMediaRepository.class, "findById", Optional.of(itemMedia));
        itemFeatureRepository = repository(ArchiveItemFeatureRepository.class, "findById", Optional.of(itemFeature));
        createService();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.create(input("0.100000", "0.100000", "0.200000", "0.200000"))
        );

        assertEquals("Media and feature must belong to the same archive item", exception.getMessage());
    }

    @Test
    void createsValidAnnotation() {
        ArchiveItemMedia itemMedia = itemMedia(1L, 10L);
        ArchiveItemFeature itemFeature = itemFeature(1L, 20L);
        AtomicReference<MediaFeatureAnnotation> saved = new AtomicReference<>();
        itemMediaRepository = repository(ArchiveItemMediaRepository.class, "findById", Optional.of(itemMedia));
        itemFeatureRepository = repository(ArchiveItemFeatureRepository.class, "findById", Optional.of(itemFeature));
        annotationRepository = repository(
                MediaFeatureAnnotationRepository.class,
                "save",
                arguments -> {
                    MediaFeatureAnnotation annotation = (MediaFeatureAnnotation) arguments[0];
                    saved.set(annotation);
                    return annotation;
                }
        );
        createService();

        MediaFeatureAnnotationDetails details = service.create(
                input("0.100000", "0.200000", "0.300000", "0.400000")
        );

        assertEquals(10L, details.archiveItemMediaId());
        assertEquals(20L, details.archiveItemFeatureId());
        assertEquals(MediaFeatureAnnotationType.CROP_REGION, details.annotationType());
        assertEquals("Test annotation", saved.get().getNote());
    }

    private MediaFeatureAnnotationWriteDto input(String x, String y, String width, String height) {
        return new MediaFeatureAnnotationWriteDto(
                10L,
                20L,
                MediaFeatureAnnotationType.CROP_REGION,
                new BigDecimal(x),
                new BigDecimal(y),
                new BigDecimal(width),
                new BigDecimal(height),
                "Test annotation"
        );
    }

    private ArchiveItemMedia itemMedia(Long archiveItemId, Long itemMediaId) {
        ArchiveItem archiveItem = new ArchiveItem();
        EntityTestUtils.setId(archiveItem, archiveItemId);

        ArchiveItemMedia itemMedia = new ArchiveItemMedia();
        EntityTestUtils.setId(itemMedia, itemMediaId);
        itemMedia.setArchiveItem(archiveItem);
        return itemMedia;
    }

    private ArchiveItemFeature itemFeature(Long archiveItemId, Long itemFeatureId) {
        ArchiveItem archiveItem = new ArchiveItem();
        EntityTestUtils.setId(archiveItem, archiveItemId);

        ArchiveItemFeature itemFeature = new ArchiveItemFeature();
        EntityTestUtils.setId(itemFeature, itemFeatureId);
        itemFeature.setArchiveItem(archiveItem);
        return itemFeature;
    }

    private <T> T repository(Class<T> type, String methodName, Object result) {
        return repository(type, methodName, arguments -> result);
    }

    private <T> T repository(Class<T> type, String methodName, RepositoryCall call) {
        return proxy(type, (proxy, method, arguments) -> {
            if (method.getName().equals(methodName))
                return call.invoke(arguments);

            throw new AssertionError("Unexpected repository call: " + method.getName());
        });
    }

    private <T> T rejectingRepository(Class<T> type) {
        return proxy(type, (proxy, method, arguments) -> {
            throw new AssertionError("Unexpected repository call: " + method.getName());
        });
    }

    private <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }

    @FunctionalInterface
    private interface RepositoryCall {
        Object invoke(Object[] arguments);
    }
}
