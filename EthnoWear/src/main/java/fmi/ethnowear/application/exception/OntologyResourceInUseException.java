package fmi.ethnowear.application.exception;

import fmi.ethnowear.domain.model.ontology.OntologyReference;
import lombok.Getter;

import java.util.List;

@Getter
public abstract class OntologyResourceInUseException extends RuntimeException {

    private final List<OntologyReference> references;

    public OntologyResourceInUseException(String message, List<OntologyReference> references) {
        super(message);
        this.references = List.copyOf(references);
    }

}
