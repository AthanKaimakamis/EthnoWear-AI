package fmi.ethnowear.application.dto.document.query.retention;

import fmi.ethnowear.domain.model.media.MediaRetentionPolicy;

import java.util.List;

public record MediaCleanupEligibilityDetails(
        Long documentId,
        boolean eligible,
        MediaRetentionPolicy policy,
        int generatedMediaCount,
        List<String> blockers
) {
    public MediaCleanupEligibilityDetails {
        blockers = List.copyOf(blockers);
    }
}
