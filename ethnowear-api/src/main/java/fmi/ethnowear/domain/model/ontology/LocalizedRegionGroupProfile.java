package fmi.ethnowear.domain.model.ontology;

import java.util.List;

public record LocalizedRegionGroupProfile(
        LocalizedOntologyResource group,
        List<LocalizedOntologyResource> regions
) {
}
