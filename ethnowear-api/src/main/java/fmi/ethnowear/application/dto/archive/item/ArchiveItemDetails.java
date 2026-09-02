package fmi.ethnowear.application.dto.archive.item;

import fmi.ethnowear.application.dto.IdentifiableDto;
import fmi.ethnowear.domain.model.archive.ArchiveType;
import fmi.ethnowear.domain.model.archive.PublicationStatus;
import fmi.ethnowear.domain.model.archive.TrustedLevel;
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
        @NotNull PublicationStatus publicationStatus,
        String ontologyRegionIri,
        String ontologyRegionLocalName,
        String ontologyRegionalEmbroideryIri,
        String ontologyRegionalEmbroideryLocalName,
        String ontologyRegionalMotifIri,
        String ontologyRegionalMotifLocalName,
        LocalDateTime submittedAt,
        LocalDateTime publishedAt,
        LocalDateTime archivedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) implements IdentifiableDto {
    public ArchiveItemDetails(
            Long id,
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
            PublicationStatus publicationStatus,
            String ontologyRegionIri,
            String ontologyRegionLocalName,
            String ontologyRegionalEmbroideryIri,
            String ontologyRegionalEmbroideryLocalName,
            LocalDateTime submittedAt,
            LocalDateTime publishedAt,
            LocalDateTime archivedAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        this(id, sourceReferenceId, collectionId, inventoryNumber, titleBg, titleEn,
                descriptionBg, descriptionEn, archiveType, periodText, originText,
                currentLocation, trustedLevel, publicationStatus, ontologyRegionIri,
                ontologyRegionLocalName, ontologyRegionalEmbroideryIri,
                ontologyRegionalEmbroideryLocalName, null, null, submittedAt, publishedAt,
                archivedAt, createdAt, updatedAt);
    }
}
