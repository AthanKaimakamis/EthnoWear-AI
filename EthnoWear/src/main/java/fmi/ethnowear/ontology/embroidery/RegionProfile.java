package fmi.ethnowear.ontology.embroidery;

import fmi.ethnowear.ontology.model.OntologyResource;

import java.util.List;

public record RegionProfile(OntologyResource region,
                            List<OntologyResource> ornaments,
                            List<OntologyResource> colors,
                            List<OntologyResource> techniques) {
}
