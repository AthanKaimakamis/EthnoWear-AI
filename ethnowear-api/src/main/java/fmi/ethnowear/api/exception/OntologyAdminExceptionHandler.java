package fmi.ethnowear.api.exception;

import fmi.ethnowear.application.exception.*;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice(basePackages = "fmi.ethnowear.api.controller.ontology")
public class OntologyAdminExceptionHandler extends BaseExceptionHandler {

    @ExceptionHandler(OntologyEntityException.class)
    public ResponseEntity<Map<String, Object>> ontologyEntity(@NonNull OntologyEntityException ex) {
        HttpStatus status = switch (ex.getReason()) {
            case INVALID -> HttpStatus.BAD_REQUEST;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case ALREADY_EXISTS, IN_USE -> HttpStatus.CONFLICT;
        };

        if (ex.getReferences().isEmpty())
            return error(status, ex.getMessage());

        return error(status, ex.getMessage(), Map.of("references", ex.getReferences()));
    }

    @ExceptionHandler({
            InvalidOrnamentException.class,
            InvalidTechniqueException.class
    })
    public ResponseEntity<Map<String, Object>> invalid(@NonNull RuntimeException ex) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler({
            OrnamentNotFoundException.class,
            TechniqueNotFoundException.class
    })
    public ResponseEntity<Map<String, Object>> notFound(@NonNull RuntimeException ex) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler({
            OrnamentAlreadyExistsException.class,
            TechniqueAlreadyExistsException.class
    })
    public ResponseEntity<Map<String, Object>> alreadyExists(@NonNull RuntimeException ex) {
        return error(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler({
            OrnamentInUseException.class,
            TechniqueInUseException.class
    })
    public ResponseEntity<Map<String, Object>> inUse(@NonNull OntologyResourceInUseException ex) {
        return error(HttpStatus.CONFLICT, ex.getMessage(), Map.of("references", ex.getReferences()));
    }
}
