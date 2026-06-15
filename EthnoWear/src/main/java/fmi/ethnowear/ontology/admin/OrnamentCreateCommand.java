package fmi.ethnowear.ontology.admin;

import java.util.List;
import java.util.Set;

public record OrnamentCreateCommand(
        String localName,
        Set<String> typeLocalNames,
        String labelBg,
        String labelEn,
        List<String> altLabelsBg,
        List<String> altLabelsEn,
        String commentBg,
        String commentEn,
        Set<String> characteristicRegionLocalNames
) {
    public OrnamentCreateCommand {
        typeLocalNames = typeLocalNames == null ? Set.of() : Set.copyOf(typeLocalNames);
        altLabelsBg = altLabelsBg == null ? List.of() : List.copyOf(altLabelsBg);
        altLabelsEn = altLabelsEn == null ? List.of() : List.copyOf(altLabelsEn);
        characteristicRegionLocalNames = characteristicRegionLocalNames == null
                ? Set.of()
                : Set.copyOf(characteristicRegionLocalNames);
    }
}
