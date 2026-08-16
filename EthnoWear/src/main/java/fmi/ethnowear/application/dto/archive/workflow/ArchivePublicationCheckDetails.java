package fmi.ethnowear.application.dto.archive.workflow;

import fmi.ethnowear.domain.model.archive.ArchivePublicationRequirement;

public record ArchivePublicationCheckDetails(
        ArchivePublicationRequirement requirement,
        boolean satisfied,
        boolean blocking
) {
}
