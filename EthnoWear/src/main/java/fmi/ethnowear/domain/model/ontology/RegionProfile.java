package fmi.ethnowear.domain.model.ontology;

import java.util.List;

public record RegionProfile(
        OntologyResource region,
        List<OntologyResource> ornaments,
        List<OntologyResource> colors,
        List<OntologyResource> techniques
) {
}
