package fmi.ethnowear.application.dto.archive.query;

import java.util.List;

public record RegionalEmbroideryArchiveOverviewDetails(
        String language,
        List<RegionalEmbroideryArchiveSectionDetails> sections
) {
    public RegionalEmbroideryArchiveOverviewDetails {
        sections = List.copyOf(sections);
    }
}
