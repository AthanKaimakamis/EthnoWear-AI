package fmi.ethnowear.ontology;

import java.util.List;

public record LocalizedOntologyResource(String iri,
                                        String localName,
                                        String label,
                                        List<String> altLabels,
                                        OntologyLanguage language) {
}
