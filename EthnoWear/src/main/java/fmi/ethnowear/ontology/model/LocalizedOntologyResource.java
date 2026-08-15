package fmi.ethnowear.ontology.model;

import fmi.ethnowear.ontology.enums.OntologyLanguage;

import java.util.List;

public record LocalizedOntologyResource(String iri,
                                        String localName,
                                        String label,
                                        List<String> altLabels,
                                        String comment,
                                        OntologyLanguage language) {
}
