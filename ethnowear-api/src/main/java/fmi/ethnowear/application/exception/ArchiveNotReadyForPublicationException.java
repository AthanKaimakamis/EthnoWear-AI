package fmi.ethnowear.application.exception;

import fmi.ethnowear.domain.model.archive.ArchivePublicationRequirement;
import lombok.Getter;

import java.util.List;

@Getter
public class ArchiveNotReadyForPublicationException extends RuntimeException {

    private final Long archiveItemId;
    private final List<ArchivePublicationRequirement> failedRequirements;

    public ArchiveNotReadyForPublicationException(
            Long archiveItemId,
            List<ArchivePublicationRequirement> failedRequirements
    ) {
        super("Archive item is not ready for publication: " + archiveItemId);
        this.archiveItemId = archiveItemId;
        this.failedRequirements = List.copyOf(failedRequirements);
    }
}
