package fmi.ethnowear.application.service.archive.workflow;

import fmi.ethnowear.testutil.EntityTestUtils;

import fmi.ethnowear.application.exception.ArchiveNotReadyForPublicationException;
import fmi.ethnowear.application.exception.InvalidPublicationTransitionException;
import fmi.ethnowear.application.service.archive.item.ArchiveItemMapper;
import fmi.ethnowear.domain.model.archive.PublicationStatus;
import fmi.ethnowear.persistence.jpa.entity.ArchiveItem;
import fmi.ethnowear.persistence.jpa.entity.SourceReference;
import fmi.ethnowear.persistence.jpa.repository.ArchiveItemFeatureRepository;
import fmi.ethnowear.persistence.jpa.repository.ArchiveItemMediaRepository;
import fmi.ethnowear.persistence.jpa.repository.ArchiveItemRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static fmi.ethnowear.support.RepositoryTestProxies.proxy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ArchivePublicationServiceTest {

    @Test
    void submitsReadyDraftForReview() {
        Fixture fixture = fixture(readyItem(PublicationStatus.DRAFT));

        fixture.service().submit(1L);

        assertEquals(PublicationStatus.IN_REVIEW, fixture.item().getPublicationStatus());
        assertNotNull(fixture.item().getSubmittedAt());
    }

    @Test
    void rejectsSubmissionWhenBlockingRequirementsFail() {
        Fixture fixture = fixture(incompleteItem(PublicationStatus.DRAFT));

        ArchiveNotReadyForPublicationException exception = assertThrows(
                ArchiveNotReadyForPublicationException.class,
                () -> fixture.service().submit(1L)
        );

        assertEquals(1L, exception.getArchiveItemId());
        assertEquals(PublicationStatus.DRAFT, fixture.item().getPublicationStatus());
    }

    @Test
    void rejectsSubmissionWithoutValidatedOntologyFeatures() {
        Fixture fixture = fixture(readyItem(PublicationStatus.DRAFT), false);

        assertThrows(
                ArchiveNotReadyForPublicationException.class,
                () -> fixture.service().submit(1L)
        );
    }

    @Test
    void returnsReviewedItemToDraft() {
        Fixture fixture = fixture(readyItem(PublicationStatus.IN_REVIEW));

        fixture.service().returnToDraft(1L);

        assertEquals(PublicationStatus.DRAFT, fixture.item().getPublicationStatus());
        assertNull(fixture.item().getSubmittedAt());
    }

    @Test
    void revalidatesBeforePublishing() {
        Fixture fixture = fixture(incompleteItem(PublicationStatus.IN_REVIEW));

        assertThrows(
                ArchiveNotReadyForPublicationException.class,
                () -> fixture.service().publish(1L)
        );

        assertEquals(PublicationStatus.IN_REVIEW, fixture.item().getPublicationStatus());
    }

    @Test
    void publishesReadyReviewedItem() {
        Fixture fixture = fixture(readyItem(PublicationStatus.IN_REVIEW));

        fixture.service().publish(1L);

        assertEquals(PublicationStatus.PUBLISHED, fixture.item().getPublicationStatus());
        assertNotNull(fixture.item().getPublishedAt());
    }

    @Test
    void archivesPublishedItem() {
        Fixture fixture = fixture(readyItem(PublicationStatus.PUBLISHED));

        fixture.service().archive(1L);

        assertEquals(PublicationStatus.ARCHIVED, fixture.item().getPublicationStatus());
        assertNotNull(fixture.item().getArchivedAt());
    }

    @Test
    void rejectsInvalidTransition() {
        Fixture fixture = fixture(readyItem(PublicationStatus.DRAFT));

        InvalidPublicationTransitionException exception = assertThrows(
                InvalidPublicationTransitionException.class,
                () -> fixture.service().publish(1L)
        );

        assertEquals(PublicationStatus.DRAFT, exception.getCurrentStatus());
        assertEquals(PublicationStatus.PUBLISHED, exception.getTargetStatus());
    }

    private Fixture fixture(ArchiveItem item) {
        return fixture(item, true);
    }

    private Fixture fixture(ArchiveItem item, boolean hasFeatures) {
        AtomicReference<ArchiveItem> stored = new AtomicReference<>(item);
        ArchiveItemRepository itemRepository = proxy(
                ArchiveItemRepository.class,
                (ignored, method, arguments) -> switch(method.getName()) {
                    case "findById" -> Optional.ofNullable(stored.get());
                    case "save" -> {
                        stored.set((ArchiveItem) arguments[0]);
                        yield stored.get();
                    }
                    default -> throw new AssertionError(
                            "Unexpected archive item repository call: " + method.getName()
                    );
                }
        );
        ArchiveItemFeatureRepository featureRepository = proxy(
                ArchiveItemFeatureRepository.class,
                (ignored, method, arguments) -> {
                    if(method.getName().equals("existsByArchiveItem_Id"))
                        return hasFeatures;

                    if(method.getName().equals("existsByArchiveItem_IdAndValidatedFalse"))
                        return false;

                    throw new AssertionError(
                            "Unexpected feature repository call: " + method.getName()
                    );
                }
        );
        ArchiveItemMediaRepository mediaRepository = proxy(
                ArchiveItemMediaRepository.class,
                (ignored, method, arguments) -> {
                    if(method.getName().equals("existsByArchiveItem_Id")
                            || method.getName().equals("existsByArchiveItem_IdAndRole"))
                        return false;

                    throw new AssertionError(
                            "Unexpected media repository call: " + method.getName()
                    );
                }
        );
        ArchivePublicationValidator validator = new ArchivePublicationValidator(
                itemRepository,
                featureRepository,
                mediaRepository
        );
        ArchivePublicationService service = new ArchivePublicationService(
                itemRepository,
                validator,
                new ArchiveItemMapper()
        );

        return new Fixture(service, stored);
    }

    private ArchiveItem readyItem(PublicationStatus status) {
        ArchiveItem item = incompleteItem(status);
        item.setTitleEn("Archive item");
        return item;
    }

    private ArchiveItem incompleteItem(PublicationStatus status) {
        SourceReference reference = new SourceReference();
        EntityTestUtils.setId(reference, 2L);

        ArchiveItem item = new ArchiveItem();
        EntityTestUtils.setId(item, 1L);
        item.setSourceReference(reference);
        item.setPublicationStatus(status);
        return item;
    }

    private record Fixture(
            ArchivePublicationService service,
            AtomicReference<ArchiveItem> stored
    ) {
        private ArchiveItem item() {
            return stored.get();
        }
    }
}
