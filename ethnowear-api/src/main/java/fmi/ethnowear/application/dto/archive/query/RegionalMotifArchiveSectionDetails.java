package fmi.ethnowear.application.dto.archive.query;

import fmi.ethnowear.application.dto.catalogue.EntityCardDetails;

import java.util.List;

public record RegionalMotifArchiveSectionDetails(
        EntityCardDetails regionalMotif,
        long totalItems,
        List<ArchiveEvidenceDetails> previewItems
) {
    public RegionalMotifArchiveSectionDetails {
        previewItems = List.copyOf(previewItems);
    }
}
