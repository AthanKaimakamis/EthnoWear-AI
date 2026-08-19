package fmi.ethnowear.application.service.archive.workflow;

import fmi.ethnowear.testutil.EntityTestUtils;

import fmi.ethnowear.application.exception.ArchiveItemNotEditableException;
import fmi.ethnowear.domain.model.archive.PublicationStatus;
import fmi.ethnowear.persistence.jpa.entity.ArchiveItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ArchiveItemWorkflowGuardTest {

    private final ArchiveItemWorkflowGuard guard =
            new ArchiveItemWorkflowGuard();

    @Test
    void permitsDraftArchiveItem() {
        ArchiveItem item = item(PublicationStatus.DRAFT);

        assertDoesNotThrow(() -> guard.requireDraft(item));
    }

    @ParameterizedTest
    @EnumSource(
            value = PublicationStatus.class,
            names = { "IN_REVIEW", "PUBLISHED", "ARCHIVED" }
    )
    void rejectsArchiveItemOutsideDraft(PublicationStatus status) {
        ArchiveItem item = item(status);

        ArchiveItemNotEditableException exception = assertThrows(
                ArchiveItemNotEditableException.class,
                () -> guard.requireDraft(item)
        );

        assertEquals(1L, exception.getArchiveItemId());
        assertEquals(status, exception.getPublicationStatus());
    }

    private ArchiveItem item(PublicationStatus status) {
        ArchiveItem item = new ArchiveItem();
        EntityTestUtils.setId(item, 1L);
        item.setPublicationStatus(status);
        return item;
    }
}
