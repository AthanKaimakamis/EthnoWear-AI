package fmi.ethnowear.application.service.archive.item;

import fmi.ethnowear.application.dto.archive.item.ArchiveItemDetails;
import fmi.ethnowear.application.dto.archive.item.ArchiveItemWriteDto;
import fmi.ethnowear.persistence.jpa.entity.ArchiveItem;
import fmi.ethnowear.persistence.jpa.entity.SourceReference;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

@Component
public class ArchiveItemMapper {

    public void apply(@NonNull ArchiveItem item, @NonNull ArchiveItemWriteDto input, SourceReference reference) {
        item.setSourceReference(reference);
        item.setCollectionId(input.collectionId());
        item.setInventoryNumber(input.inventoryNumber());
        item.setTitleBg(input.titleBg());
        item.setTitleEn(input.titleEn());
        item.setDescriptionBg(input.descriptionBg());
        item.setDescriptionEn(input.descriptionEn());
        item.setArchiveType(input.archiveType());
        item.setPeriodText(input.periodText());
        item.setOriginText(input.originText());
        item.setCurrentLocation(input.currentLocation());
        item.setTrustedLevel(input.trustedLevel());
        item.setOntologyRegionIri(input.ontologyRegionIri());
        item.setOntologyRegionLocalName(input.ontologyRegionLocalName());
        item.setOntologyRegionalEmbroideryIri(input.ontologyRegionalEmbroideryIri());
        item.setOntologyRegionalEmbroideryLocalName(input.ontologyRegionalEmbroideryLocalName());
        item.setOntologyRegionalMotifIri(input.ontologyRegionalMotifIri());
        item.setOntologyRegionalMotifLocalName(input.ontologyRegionalMotifLocalName());
    }

    public ArchiveItemDetails toDetails(@NonNull ArchiveItem item) {
        return new ArchiveItemDetails(
                item.getId(),
                item.getSourceReference().getId(),
                item.getCollectionId(),
                item.getInventoryNumber(),
                item.getTitleBg(),
                item.getTitleEn(),
                item.getDescriptionBg(),
                item.getDescriptionEn(),
                item.getArchiveType(),
                item.getPeriodText(),
                item.getOriginText(),
                item.getCurrentLocation(),
                item.getTrustedLevel(),
                item.getPublicationStatus(),
                item.getOntologyRegionIri(),
                item.getOntologyRegionLocalName(),
                item.getOntologyRegionalEmbroideryIri(),
                item.getOntologyRegionalEmbroideryLocalName(),
                item.getOntologyRegionalMotifIri(),
                item.getOntologyRegionalMotifLocalName(),
                item.getSubmittedAt(),
                item.getPublishedAt(),
                item.getArchivedAt(),
                item.getCreatedAt(),
                item.getUpdatedAt()
        );
    }
}
