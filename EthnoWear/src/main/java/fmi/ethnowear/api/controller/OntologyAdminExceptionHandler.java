package fmi.ethnowear.api.controller;

import fmi.ethnowear.ontology.admin.InvalidOrnamentException;
import fmi.ethnowear.ontology.admin.InvalidTechniqueException;
import fmi.ethnowear.ontology.admin.OrnamentAlreadyExistsException;
import fmi.ethnowear.ontology.admin.OrnamentInUseException;
import fmi.ethnowear.ontology.admin.OrnamentNotFoundException;
import fmi.ethnowear.ontology.admin.OntologyEntityException;
import fmi.ethnowear.ontology.admin.TechniqueAlreadyExistsException;
import fmi.ethnowear.ontology.admin.TechniqueInUseException;
import fmi.ethnowear.ontology.admin.TechniqueNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice(assignableTypes = {
        OrnamentOntologyController.class,
        TechniqueOntologyController.class,
        OntologyEntityController.class
})
public class OntologyAdminExceptionHandler {

    @ExceptionHandler(OntologyEntityException.class)
    public ResponseEntity<Map<String, Object>> ontologyEntity(OntologyEntityException exception) {
        HttpStatus status = switch (exception.getReason()) {
            case INVALID -> HttpStatus.BAD_REQUEST;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case ALREADY_EXISTS, IN_USE -> HttpStatus.CONFLICT;
        };
        if (exception.getReferences().isEmpty()) {
            return error(status, exception.getMessage());
        }
        return ResponseEntity.status(status).body(Map.of(
                "status", status.value(), "error", status.getReasonPhrase(),
                "message", exception.getMessage(), "references", exception.getReferences()));
    }

    @ExceptionHandler(InvalidOrnamentException.class)
    public ResponseEntity<Map<String, Object>> invalidOrnament(InvalidOrnamentException exception) {
        return error(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(InvalidTechniqueException.class)
    public ResponseEntity<Map<String, Object>> invalidTechnique(InvalidTechniqueException exception) {
        return error(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(OrnamentNotFoundException.class)
    public ResponseEntity<Map<String, Object>> ornamentNotFound(OrnamentNotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(TechniqueNotFoundException.class)
    public ResponseEntity<Map<String, Object>> techniqueNotFound(TechniqueNotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(OrnamentAlreadyExistsException.class)
    public ResponseEntity<Map<String, Object>> ornamentAlreadyExists(
            OrnamentAlreadyExistsException exception
    ) {
        return error(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(TechniqueAlreadyExistsException.class)
    public ResponseEntity<Map<String, Object>> techniqueAlreadyExists(
            TechniqueAlreadyExistsException exception
    ) {
        return error(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(OrnamentInUseException.class)
    public ResponseEntity<Map<String, Object>> ornamentInUse(OrnamentInUseException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "status", HttpStatus.CONFLICT.value(),
                "error", HttpStatus.CONFLICT.getReasonPhrase(),
                "message", exception.getMessage(),
                "references", exception.getReferences()
        ));
    }

    @ExceptionHandler(TechniqueInUseException.class)
    public ResponseEntity<Map<String, Object>> techniqueInUse(TechniqueInUseException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "status", HttpStatus.CONFLICT.value(),
                "error", HttpStatus.CONFLICT.getReasonPhrase(),
                "message", exception.getMessage(),
                "references", exception.getReferences()
        ));
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of(
                "status", status.value(),
                "error", status.getReasonPhrase(),
                "message", message
        ));
    }
}
