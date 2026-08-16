package fmi.ethnowear.application.exception;

import fmi.ethnowear.domain.model.archive.PublicationStatus;
import lombok.Getter;

@Getter
public class ArchiveItemNotEditableException extends RuntimeException {

    private final Long archiveItemId;
    private final PublicationStatus publicationStatus;

    public ArchiveItemNotEditableException(Long archiveItemId, PublicationStatus publicationStatus) {
        super("Archive item " + archiveItemId
                + " cannot be modified while its status is "
                + publicationStatus
        );
        this.archiveItemId = archiveItemId;
        this.publicationStatus = publicationStatus;
    }
}
