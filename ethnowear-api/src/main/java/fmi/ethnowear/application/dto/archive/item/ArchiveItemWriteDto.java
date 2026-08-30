package fmi.ethnowear.application.dto.archive.item;

import fmi.ethnowear.domain.model.archive.ArchiveType;
import fmi.ethnowear.domain.model.archive.TrustedLevel;
import jakarta.validation.constraints.NotNull;

public record ArchiveItemWriteDto(
        @NotNull Long sourceReferenceId,
        String collectionId,
        String inventoryNumber,
        String titleBg,
        String titleEn,
        String descriptionBg,
        String descriptionEn,
        @NotNull ArchiveType archiveType,
        String periodText,
        String originText,
        String currentLocation,
        @NotNull TrustedLevel trustedLevel,
        String ontologyRegionIri,
        String ontologyRegionLocalName,
        String ontologyRegionalEmbroideryIri,
        String ontologyRegionalEmbroideryLocalName
) {
}
