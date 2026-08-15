package fmi.ethnowear.domain.model.ontology;

import java.util.List;

public record LocalizedRegionProfile(
        LocalizedOntologyResource region,
        List<LocalizedOntologyResource> ornaments,
        List<LocalizedOntologyResource> colors,
        List<LocalizedOntologyResource> techniques
) {
}
