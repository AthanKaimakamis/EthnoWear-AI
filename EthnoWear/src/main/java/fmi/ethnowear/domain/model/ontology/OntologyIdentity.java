package fmi.ethnowear.domain.model.ontology;

import static fmi.ethnowear.util.TextUtils.isBlank;

public record OntologyIdentity(
        String iri,
        String localName
) {
    public boolean isComplete() {
        return !isBlank(iri) && !isBlank(localName);
    }

    public boolean isAbsent() {
        return isBlank(iri) && isBlank(localName);
    }

    public boolean isIncomplete() {
        return !isAbsent() && !isComplete();
    }
}
