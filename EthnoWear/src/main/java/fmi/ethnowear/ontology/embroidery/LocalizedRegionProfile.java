package fmi.ethnowear.ontology.embroidery;

import fmi.ethnowear.ontology.LocalizedOntologyResource;

import java.util.List;

public record LocalizedRegionProfile(LocalizedOntologyResource region,
                                     List<LocalizedOntologyResource> ornaments,
                                     List<LocalizedOntologyResource> colors,
                                     List<LocalizedOntologyResource> techniques) {
}
