package fmi.ethnowear.api.dto.archive.query;

import fmi.ethnowear.application.enums.ArchiveType;
import fmi.ethnowear.application.enums.TrustedLevel;

import java.util.List;

public record ArchiveEvidenceDetails(
        Long archiveItemId,
        ArchiveType archiveType,
        String titleBg,
        String titleEn,
        String periodText,
        String originText,
        String currentLocation,
        TrustedLevel trustedLevel,
        boolean directlyLinked,
        List<ArchiveEvidenceFeatureDetails> matchingFeatures,
        ArchiveEvidenceMediaDetails previewMedia
) {
}
