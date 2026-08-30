package fmi.ethnowear.application.dto.archive.query;

import fmi.ethnowear.domain.model.archive.ArchiveType;
import fmi.ethnowear.domain.model.archive.TrustedLevel;

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
