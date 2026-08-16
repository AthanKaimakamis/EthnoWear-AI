package fmi.ethnowear.application.service.archive.workflow;

import fmi.ethnowear.application.exception.ArchiveItemNotEditableException;
import fmi.ethnowear.domain.model.archive.PublicationStatus;
import fmi.ethnowear.persistence.jpa.entity.ArchiveItem;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

@Component
public class ArchiveItemWorkflowGuard {

    public void requireDraft(@NotNull ArchiveItem item) {
        if(item.getPublicationStatus() != PublicationStatus.DRAFT)
            throw new ArchiveItemNotEditableException(item.getId(), item.getPublicationStatus());
    }
}
