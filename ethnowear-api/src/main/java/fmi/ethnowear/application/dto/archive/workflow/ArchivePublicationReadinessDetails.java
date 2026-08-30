package fmi.ethnowear.application.dto.archive.workflow;

import fmi.ethnowear.domain.model.archive.PublicationStatus;

import java.util.List;

public record ArchivePublicationReadinessDetails(
        Long archiveItemId,
        PublicationStatus publicationStatus,
        boolean ready,
        List<ArchivePublicationCheckDetails> checks
) {
    public ArchivePublicationReadinessDetails {
        checks = List.copyOf(checks);
    }
}
