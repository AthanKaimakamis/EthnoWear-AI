package fmi.ethnowear.api.exception;

import fmi.ethnowear.application.exception.*;
import jakarta.validation.ConstraintViolationException;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice(basePackages = "fmi.ethnowear.api.controller.document")
public class DocumentApiExceptionHandler extends BaseExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> notFound(
            @NonNull ResourceNotFoundException ex
    ) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler({
            InvalidDocumentJobTransitionException.class,
            InvalidDocumentPageReviewTransitionException.class,
            InvalidDocumentPageProvenanceTransitionException.class,
            ActiveDocumentJobExistsException.class
    })
    public ResponseEntity<Map<String, Object>> conflict(
            @NonNull RuntimeException ex
    ) {
        return error(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler({
            InvalidDocumentProcessingRequestException.class,
            UnprocessableDocumentEvidenceException.class
    })
    public ResponseEntity<Map<String, Object>> unprocessable(
            @NonNull RuntimeException ex
    ) {
        return error(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
    }

    @ExceptionHandler({
            IllegalArgumentException.class,
            ConstraintViolationException.class
    })
    public ResponseEntity<Map<String, Object>> invalidInput(
            @NonNull RuntimeException ex
    ) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage());
    }
}