package fmi.ethnowear.api.exception;

import fmi.ethnowear.application.exception.*;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice(basePackages = "fmi.ethnowear.api.controller.archive")
public class ArchiveApiExceptionHandler extends BaseExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> notFound(@NonNull RuntimeException ex) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(ResourceInUseException.class)
    public ResponseEntity<Map<String, Object>> inUse(@NonNull ResourceInUseException ex) {
        return error(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(InvalidPublicationTransitionException.class)
    public ResponseEntity<Map<String, Object>> invalidPublicationTransition(@NonNull InvalidPublicationTransitionException ex) {
        return error(
                HttpStatus.CONFLICT,
                ex.getMessage(),
                Map.of(
                        "archiveItemId", ex.getArchiveItemId(),
                        "currentStatus", ex.getCurrentStatus(),
                        "targetStatus", ex.getTargetStatus()
                )
        );
    }

    @ExceptionHandler(ArchiveNotReadyForPublicationException.class)
    public ResponseEntity<Map<String, Object>> archiveNotReady(@NonNull ArchiveNotReadyForPublicationException ex) {
        return error(
                HttpStatus.CONFLICT,
                ex.getMessage(),
                Map.of(
                        "archiveItemId", ex.getArchiveItemId(),
                        "failedRequirements", ex.getFailedRequirements()
                )
        );
    }

    @ExceptionHandler(ArchiveItemNotEditableException.class)
    public ResponseEntity<Map<String, Object>> archiveItemNotEditable(@NonNull ArchiveItemNotEditableException ex) {
        return error(
                HttpStatus.CONFLICT,
                ex.getMessage(),
                Map.of(
                        "archiveItemId", ex.getArchiveItemId(),
                        "publicationStatus", ex.getPublicationStatus()
                )
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> invalidInput(@NonNull IllegalArgumentException ex) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

}
