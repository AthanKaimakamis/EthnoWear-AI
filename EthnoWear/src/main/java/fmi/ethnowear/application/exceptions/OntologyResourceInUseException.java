package fmi.ethnowear.application.exceptions;

import fmi.ethnowear.ontology.admin.model.OntologyReference;
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
