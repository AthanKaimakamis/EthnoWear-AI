package fmi.ethnowear.api.dto.archive.item;

import fmi.ethnowear.application.enums.ArchiveType;
import fmi.ethnowear.application.enums.TrustedLevel;
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
