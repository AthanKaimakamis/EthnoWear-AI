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
        String ontologyRegionalEmbroideryLocalName,
        String ontologyRegionalMotifIri,
        String ontologyRegionalMotifLocalName
) {
    public ArchiveItemWriteDto(
            Long sourceReferenceId,
            String collectionId,
            String inventoryNumber,
            String titleBg,
            String titleEn,
            String descriptionBg,
            String descriptionEn,
            ArchiveType archiveType,
            String periodText,
            String originText,
            String currentLocation,
            TrustedLevel trustedLevel,
            String ontologyRegionIri,
            String ontologyRegionLocalName,
            String ontologyRegionalEmbroideryIri,
            String ontologyRegionalEmbroideryLocalName
    ) {
        this(sourceReferenceId, collectionId, inventoryNumber, titleBg, titleEn,
                descriptionBg, descriptionEn, archiveType, periodText, originText,
                currentLocation, trustedLevel, ontologyRegionIri, ontologyRegionLocalName,
                ontologyRegionalEmbroideryIri, ontologyRegionalEmbroideryLocalName, null, null);
    }
}
