package fmi.ethnowear.domain.model.ontology;

import java.util.List;

public record LocalizedOntologyResource(
        String iri,
        String localName,
        String label,
        List<String> altLabels,
        String comment,
        OntologyLanguage language
) {
}
