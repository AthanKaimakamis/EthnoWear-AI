package fmi.ethnowear.application.port.ontology.admin;

import java.util.Collection;

public interface OntologyUsageGuard {
    void requireUnused(Collection<String> ontologyIris);
}
