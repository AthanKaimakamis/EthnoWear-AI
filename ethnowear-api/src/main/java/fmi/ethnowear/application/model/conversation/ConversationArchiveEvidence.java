package fmi.ethnowear.application.model.conversation;

import fmi.ethnowear.domain.model.archive.ArchiveType;
import fmi.ethnowear.domain.model.archive.TrustedLevel;

public record ConversationArchiveEvidence(
        String citationId,
        Long archiveItemId,
        String title,
        String description,
        ArchiveType archiveType,
        String periodText,
        String originText,
        String currentLocation,
        TrustedLevel trustedLevel,
        Long sourceReferenceId
) {
}
