package fmi.ethnowear.application.dto.archive.query;

import java.util.List;

public record RegionalMotifArchiveOverviewDetails(
        String language,
        List<RegionalMotifArchiveSectionDetails> sections
) {
    public RegionalMotifArchiveOverviewDetails {
        sections = List.copyOf(sections);
    }
}
