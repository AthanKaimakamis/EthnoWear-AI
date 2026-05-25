package fmi.ethnowear.ontology.embroidery;

import fmi.ethnowear.ontology.model.LocalizedOntologyResource;

import java.util.List;

public record LocalizedRegionGroupProfile(
        LocalizedOntologyResource group,
        List<LocalizedOntologyResource> regions
) {
}
