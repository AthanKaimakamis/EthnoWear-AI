package fmi.ethnowear.application.dto.archive.query;

import fmi.ethnowear.application.dto.catalogue.EntityCardDetails;

import java.util.List;

public record RegionalEmbroideryArchiveSectionDetails(
        EntityCardDetails regionalEmbroidery,
        long totalItems,
        List<ArchiveEvidenceDetails> previewItems
) {
    public RegionalEmbroideryArchiveSectionDetails {
        previewItems = List.copyOf(previewItems);
    }
}
