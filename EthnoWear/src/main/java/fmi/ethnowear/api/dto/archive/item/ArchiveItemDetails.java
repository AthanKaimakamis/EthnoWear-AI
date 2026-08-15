package fmi.ethnowear.api.dto.archive.item;

import fmi.ethnowear.api.dto.IdentifiableDto;
import fmi.ethnowear.application.enums.ArchiveType;
import fmi.ethnowear.application.enums.TrustedLevel;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record ArchiveItemDetails(
        @NotNull Long id,
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
        String ontologyRegionalEmbroideryLocalName,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) implements IdentifiableDto {
}
