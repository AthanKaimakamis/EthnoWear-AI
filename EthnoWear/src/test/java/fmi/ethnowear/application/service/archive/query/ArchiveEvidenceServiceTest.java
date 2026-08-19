package fmi.ethnowear.application.service.archive.query;

import fmi.ethnowear.testutil.EntityTestUtils;

import fmi.ethnowear.application.dto.archive.query.ArchiveEvidenceDetails;
import fmi.ethnowear.domain.model.archive.ArchiveType;
import fmi.ethnowear.domain.model.ontology.FeatureType;
import fmi.ethnowear.domain.model.archive.MediaRole;
import fmi.ethnowear.domain.model.archive.MediaType;
import fmi.ethnowear.domain.model.archive.TrustedLevel;
import fmi.ethnowear.persistence.jpa.entity.ArchiveItem;
import fmi.ethnowear.persistence.jpa.entity.ArchiveItemFeature;
import fmi.ethnowear.persistence.jpa.entity.ArchiveItemMedia;
import fmi.ethnowear.persistence.jpa.entity.MediaAsset;
import fmi.ethnowear.persistence.jpa.repository.ArchiveItemFeatureRepository;
import fmi.ethnowear.persistence.jpa.repository.ArchiveItemMediaRepository;
import fmi.ethnowear.persistence.jpa.repository.ArchiveItemRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArchiveEvidenceServiceTest {

    private static final String REGION_IRI = "http://example.org/ontology#SofiaRegion";

    @Test
    void returnsDirectAndFeatureEvidenceWithPreferredPreview() {
        ArchiveItem item = item();
        ArchiveItemFeature feature = feature(item);
        ArchiveItemMedia primary = media(item, 30L, MediaRole.PRIMARY, "primary.jpg");
        ArchiveItemMedia thumbnail = media(item, 31L, MediaRole.THUMBNAIL, "thumbnail.jpg");
        Pageable pageable = PageRequest.of(0, 12);

        ArchiveItemRepository itemRepository = proxy(ArchiveItemRepository.class, (ignored, method, arguments) -> {
            if(method.getName().equals("findOntologyEvidence")) {
                assertEquals(FeatureType.REGION, arguments[0]);
                assertEquals(REGION_IRI, arguments[1]);
                assertEquals(true, arguments[2]);
                assertEquals(false, arguments[3]);
                return new PageImpl<>(List.of(item), pageable, 1);
            }

            throw new AssertionError("Unexpected repository call: " + method.getName());
        });

        ArchiveItemFeatureRepository featureRepository = proxy(
                ArchiveItemFeatureRepository.class,
                (ignored, method, arguments) -> List.of(feature)
        );
        ArchiveItemMediaRepository mediaRepository = proxy(
                ArchiveItemMediaRepository.class,
                (ignored, method, arguments) -> List.of(primary, thumbnail)
        );

        ArchiveEvidenceService service = new ArchiveEvidenceService(
                itemRepository,
                featureRepository,
                mediaRepository,
                new ArchiveEvidenceMapper()
        );

        Page<ArchiveEvidenceDetails> result = service.findByOntologyEntity(
                FeatureType.REGION,
                REGION_IRI,
                pageable
        );

        ArchiveEvidenceDetails details = result.getContent().getFirst();
        assertTrue(details.directlyLinked());
        assertEquals(1, details.matchingFeatures().size());
        assertEquals(31L, details.previewMedia().archiveItemMediaId());
        assertEquals("thumbnail.jpg", details.previewMedia().fileName());
    }

    @Test
    void doesNotLoadEnrichmentForEmptyPage() {
        Pageable pageable = PageRequest.of(0, 12);
        ArchiveItemRepository itemRepository = proxy(
                ArchiveItemRepository.class,
                (ignored, method, arguments) -> Page.empty(pageable)
        );

        ArchiveEvidenceService service = new ArchiveEvidenceService(
                itemRepository,
                rejectingRepository(ArchiveItemFeatureRepository.class),
                rejectingRepository(ArchiveItemMediaRepository.class),
                new ArchiveEvidenceMapper()
        );

        Page<ArchiveEvidenceDetails> result = service.findByOntologyEntity(
                FeatureType.TECHNIQUE,
                "http://example.org/ontology#ChainStitch",
                pageable
        );

        assertTrue(result.isEmpty());
    }

    @Test
    void returnsFeatureEvidenceWithoutDirectLink() {
        String techniqueIri = "http://example.org/ontology#ChainStitch";
        ArchiveItem item = item();
        ArchiveItemFeature feature = feature(item);
        feature.setFeatureType(FeatureType.TECHNIQUE);
        feature.setOntologyIri(techniqueIri);
        feature.setOntologyLocalName("ChainStitch");
        Pageable pageable = PageRequest.of(0, 12);

        ArchiveItemRepository itemRepository = proxy(ArchiveItemRepository.class, (ignored, method, arguments) -> {
            assertEquals(FeatureType.TECHNIQUE, arguments[0]);
            assertEquals(techniqueIri, arguments[1]);
            assertEquals(false, arguments[2]);
            assertEquals(false, arguments[3]);
            return new PageImpl<>(List.of(item), pageable, 1);
        });
        ArchiveItemFeatureRepository featureRepository = proxy(
                ArchiveItemFeatureRepository.class,
                (ignored, method, arguments) -> List.of(feature)
        );
        ArchiveItemMediaRepository mediaRepository = proxy(
                ArchiveItemMediaRepository.class,
                (ignored, method, arguments) -> List.of()
        );

        ArchiveEvidenceService service = new ArchiveEvidenceService(
                itemRepository,
                featureRepository,
                mediaRepository,
                new ArchiveEvidenceMapper()
        );

        ArchiveEvidenceDetails details = service
                .findByOntologyEntity(FeatureType.TECHNIQUE, techniqueIri, pageable)
                .getContent()
                .getFirst();

        assertFalse(details.directlyLinked());
        assertEquals(FeatureType.TECHNIQUE, details.matchingFeatures().getFirst().featureType());
        assertNull(details.previewMedia());
    }

    @Test
    void rejectsBlankOntologyIri() {
        ArchiveEvidenceService service = new ArchiveEvidenceService(null, null, null, null);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.findByOntologyEntity(FeatureType.ORNAMENT, " ", PageRequest.of(0, 12))
        );

        assertEquals("Ontology IRI is required", exception.getMessage());
    }

    @Test
    void rejectsMissingEntityType() {
        ArchiveEvidenceService service = new ArchiveEvidenceService(null, null, null, null);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.findByOntologyEntity(null, REGION_IRI, PageRequest.of(0, 12))
        );

        assertEquals("Ontology entity type is required", exception.getMessage());
    }

    @Test
    void rejectsMissingPageable() {
        ArchiveEvidenceService service = new ArchiveEvidenceService(null, null, null, null);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.findByOntologyEntity(FeatureType.REGION, REGION_IRI, null)
        );

        assertEquals("Pageable is required", exception.getMessage());
    }

    private ArchiveItem item() {
        ArchiveItem item = new ArchiveItem();
        EntityTestUtils.setId(item, 1L);
        item.setArchiveType(ArchiveType.EMBROIDERY_SAMPLE);
        item.setTitleBg("Софийска шевица");
        item.setTitleEn("Sofia embroidery");
        item.setTrustedLevel(TrustedLevel.VERIFIED);
        item.setOntologyRegionIri(REGION_IRI);
        return item;
    }

    private ArchiveItemFeature feature(ArchiveItem item) {
        ArchiveItemFeature feature = new ArchiveItemFeature();
        EntityTestUtils.setId(feature, 20L);
        feature.setArchiveItem(item);
        feature.setFeatureType(FeatureType.REGION);
        feature.setOntologyIri(REGION_IRI);
        feature.setOntologyLocalName("SofiaRegion");
        feature.setConfidence(BigDecimal.ONE);
        feature.setValidated(true);
        return feature;
    }

    private ArchiveItemMedia media(ArchiveItem item, Long id, MediaRole role, String fileName) {
        MediaAsset asset = new MediaAsset();
        EntityTestUtils.setId(asset, id + 100);
        asset.setFileName(fileName);
        asset.setStorageUrl("/media/" + fileName);
        asset.setMediaType(MediaType.IMAGE);

        ArchiveItemMedia media = new ArchiveItemMedia();
        EntityTestUtils.setId(media, id);
        media.setArchiveItem(item);
        media.setMediaAsset(asset);
        media.setRole(role);
        return media;
    }

    private <T> T rejectingRepository(Class<T> type) {
        return proxy(type, (ignored, method, arguments) -> {
            throw new AssertionError("Unexpected repository call: " + method.getName());
        });
    }

    private <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }
}
