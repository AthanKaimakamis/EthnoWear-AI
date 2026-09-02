package fmi.ethnowear.application.port.ontology.admin;

import fmi.ethnowear.application.model.ontology.OntologyChangeMetadata;

public interface OntologyChangeMetadataProvider {

    OntologyChangeMetadata current();

    static OntologyChangeMetadataProvider systemDefault() {
        return () -> new OntologyChangeMetadata(null, "Ontology system operation");
    }
}
