package fmi.ethnowear.ontology.admin;

import java.util.List;
import java.util.Set;

public record OntologyEntityDetails(
        String iri,
        String localName,
        String labelBg,
        String labelEn,
        List<String> altLabelsBg,
        List<String> altLabelsEn,
        String commentBg,
        String commentEn,
        String regionGroupLocalName,
        String regionLocalName,
        Set<String> ornamentLocalNames,
        Set<String> techniqueLocalNames,
        Set<String> motifLocalNames
) {
}
