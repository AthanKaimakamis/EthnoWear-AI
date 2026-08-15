package fmi.ethnowear.application.service.archive.item;

import fmi.ethnowear.application.dto.archive.item.ArchiveItemDetails;
import fmi.ethnowear.application.dto.archive.item.ArchiveItemFeatureWriteDto;
import fmi.ethnowear.application.dto.archive.item.ArchiveItemWriteDto;
import fmi.ethnowear.domain.model.archive.ArchiveType;
import fmi.ethnowear.domain.model.ontology.FeatureType;
import fmi.ethnowear.domain.model.archive.TrustedLevel;
import fmi.ethnowear.application.exception.ResourceInUseException;
import fmi.ethnowear.persistence.jpa.entity.ArchiveItem;
import fmi.ethnowear.persistence.jpa.entity.SourceReference;
import fmi.ethnowear.persistence.jpa.repository.ArchiveItemFeatureRepository;
import fmi.ethnowear.persistence.jpa.repository.ArchiveItemMediaRepository;
import fmi.ethnowear.persistence.jpa.repository.ArchiveItemRepository;
import fmi.ethnowear.persistence.jpa.repository.SourceReferenceRepository;
import fmi.ethnowear.application.port.ontology.EmbroideryOntologyClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.util.Optional;

import static fmi.ethnowear.support.RepositoryTestProxies.proxy;
import static fmi.ethnowear.support.RepositoryTestProxies.rejecting;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ArchiveItemServicesTest {

    @Test
    void rejectsArchiveItemWithoutType() {
        ArchiveItemService service = itemService(
                rejecting(ArchiveItemRepository.class),
                rejecting(SourceReferenceRepository.class),
                new ArchiveItemUsageChecker(null, null)
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.create(itemInput(null))
        );

        assertEquals("Archive type is required", exception.getMessage());
    }

    @Test
    void createsArchiveItemForExistingReference() {
        SourceReference reference = new SourceReference();
        reference.setId(3L);
        SourceReferenceRepository referenceRepository = proxy(
                SourceReferenceRepository.class,
                (ignored, method, arguments) -> Optional.of(reference)
        );
        ArchiveItemRepository itemRepository = proxy(
                ArchiveItemRepository.class,
                (ignored, method, arguments) -> (ArchiveItem) arguments[0]
        );
        ArchiveItemService service = itemService(
                itemRepository,
                referenceRepository,
                new ArchiveItemUsageChecker(null, null)
        );

        ArchiveItemDetails details = service.create(itemInput(ArchiveType.EMBROIDERY_SAMPLE));

        assertEquals(3L, details.sourceReferenceId());
        assertEquals("Test archive item", details.titleEn());
    }

    @Test
    void blocksDeletionOfArchiveItemWithFeatures() {
        ArchiveItem item = new ArchiveItem();
        item.setId(4L);
        ArchiveItemRepository itemRepository = proxy(ArchiveItemRepository.class, (ignored, method, arguments) -> {
            if(method.getName().equals("findById"))
                return Optional.of(item);

            throw new AssertionError("Unexpected repository call: " + method.getName());
        });
        ArchiveItemFeatureRepository featureRepository = proxy(
                ArchiveItemFeatureRepository.class,
                (ignored, method, arguments) -> true
        );
        ArchiveItemMediaRepository mediaRepository = rejecting(ArchiveItemMediaRepository.class);
        ArchiveItemService service = itemService(
                itemRepository,
                null,
                new ArchiveItemUsageChecker(featureRepository, mediaRepository)
        );

        assertThrows(ResourceInUseException.class, () -> service.delete(4L));
    }

    @Test
    void rejectsFeatureConfidenceOutsideNormalizedRange() {
        ArchiveItemFeatureService service = new ArchiveItemFeatureService(
                rejecting(ArchiveItemFeatureRepository.class),
                rejecting(ArchiveItemRepository.class),
                rejecting(SourceReferenceRepository.class),
                new ArchiveItemFeatureMapper(),
                new ArchiveItemFeatureUsageChecker(null)
        );
        ArchiveItemFeatureWriteDto input = new ArchiveItemFeatureWriteDto(
                4L,
                FeatureType.TECHNIQUE,
                "http://example.org/ontology#ChainStitch",
                "ChainStitch",
                new BigDecimal("1.1"),
                false,
                null,
                null
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.create(input)
        );

        assertEquals("Confidence must be between 0 and 1", exception.getMessage());
    }

    @ParameterizedTest
    @EnumSource(value = FeatureType.class, names = { "REGION", "REGIONAL_EMBROIDERY" })
    void rejectsPrimaryClassificationAsArchiveItemFeature(FeatureType featureType) {
        ArchiveItemFeatureService service = new ArchiveItemFeatureService(
                rejecting(ArchiveItemFeatureRepository.class),
                rejecting(ArchiveItemRepository.class),
                rejecting(SourceReferenceRepository.class),
                new ArchiveItemFeatureMapper(),
                new ArchiveItemFeatureUsageChecker(null)
        );
        ArchiveItemFeatureWriteDto input = new ArchiveItemFeatureWriteDto(
                4L,
                featureType,
                "http://example.org/ontology#Classification",
                "Classification",
                BigDecimal.ONE,
                true,
                null,
                null
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.create(input)
        );

        assertEquals(
                "Region and regional embroidery must be assigned directly to the archive item",
                exception.getMessage()
        );
    }

    private ArchiveItemService itemService(ArchiveItemRepository itemRepository,
                                           SourceReferenceRepository referenceRepository,
                                           ArchiveItemUsageChecker usageChecker) {
        return new ArchiveItemService(
                itemRepository,
                referenceRepository,
                new ArchiveItemMapper(),
                usageChecker,
                new ArchiveItemOntologyValidator(rejecting(EmbroideryOntologyClient.class))
        );
    }

    private ArchiveItemWriteDto itemInput(ArchiveType archiveType) {
        return new ArchiveItemWriteDto(
                3L,
                null,
                null,
                null,
                "Test archive item",
                null,
                null,
                archiveType,
                null,
                null,
                null,
                TrustedLevel.VERIFIED,
                null,
                null,
                null,
                null
        );
    }
}
