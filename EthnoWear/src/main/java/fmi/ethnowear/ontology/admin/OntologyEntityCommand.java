package fmi.ethnowear.ontology.admin;

import java.util.List;
import java.util.Set;

public record OntologyEntityCommand(
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
    public OntologyEntityCommand {
        altLabelsBg = altLabelsBg == null ? List.of() : List.copyOf(altLabelsBg);
        altLabelsEn = altLabelsEn == null ? List.of() : List.copyOf(altLabelsEn);
        ornamentLocalNames = ornamentLocalNames == null ? Set.of() : Set.copyOf(ornamentLocalNames);
        techniqueLocalNames = techniqueLocalNames == null ? Set.of() : Set.copyOf(techniqueLocalNames);
        motifLocalNames = motifLocalNames == null ? Set.of() : Set.copyOf(motifLocalNames);
    }
}
