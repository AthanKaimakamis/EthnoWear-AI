package fmi.ethnowear.application.exception;

import fmi.ethnowear.domain.model.ontology.OntologyReference;

import java.util.List;

public class OntologyEntityException extends RuntimeException {
    private final Reason reason;
    private final List<OntologyReference> references;

    public OntologyEntityException(Reason reason, String message) {
        this(reason, message, List.of());
    }

    public OntologyEntityException(Reason reason, String message, List<OntologyReference> references) {
        super(message);
        this.reason = reason;
        this.references = List.copyOf(references);
    }

    public Reason getReason() { return reason; }
    public List<OntologyReference> getReferences() { return references; }

    public enum Reason { INVALID, NOT_FOUND, ALREADY_EXISTS, IN_USE }
}
