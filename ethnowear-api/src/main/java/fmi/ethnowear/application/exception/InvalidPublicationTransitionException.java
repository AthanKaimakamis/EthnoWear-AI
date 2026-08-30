package fmi.ethnowear.application.exception;

import fmi.ethnowear.domain.model.archive.PublicationStatus;
import lombok.Getter;

@Getter
public class InvalidPublicationTransitionException extends RuntimeException {

    private final Long archiveItemId;
    private final PublicationStatus currentStatus;
    private final PublicationStatus targetStatus;

    public InvalidPublicationTransitionException(
            Long archiveItemId,
            PublicationStatus currentStatus,
            PublicationStatus targetStatus
    ) {
        super("Archive item " + archiveItemId
                + " cannot transition from " + currentStatus
                + " to " + targetStatus
        );
        this.archiveItemId = archiveItemId;
        this.currentStatus = currentStatus;
        this.targetStatus = targetStatus;
    }
}
