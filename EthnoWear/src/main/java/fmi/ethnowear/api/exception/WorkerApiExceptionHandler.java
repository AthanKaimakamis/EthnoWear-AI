package fmi.ethnowear.api.exception;

import fmi.ethnowear.application.dto.worker.WorkerApiError;
import fmi.ethnowear.application.exception.*;
import jakarta.validation.ConstraintViolationException;
import org.jspecify.annotations.NonNull;
import org.springframework.http.*;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice(basePackages = "fmi.ethnowear.api.controller.worker.internal")
public class WorkerApiExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<WorkerApiError> notFound(@NonNull ResourceNotFoundException ex) {
        return error(
                HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND",
                ex.getMessage()
        );
    }

    @ExceptionHandler(WorkerClaimExpiredException.class)
    public ResponseEntity<WorkerApiError> expired(@NonNull WorkerClaimExpiredException ex) {
        return error(
                HttpStatus.GONE,
                "CLAIM_EXPIRED",
                ex.getMessage()
        );
    }

    @ExceptionHandler(WorkerClaimConflictException.class)
    public ResponseEntity<WorkerApiError> staleClaim(@NonNull WorkerClaimConflictException ex) {
        return error(
                HttpStatus.CONFLICT,
                "STALE_CLAIM",
                ex.getMessage()
        );
    }

    @ExceptionHandler(WorkerManifestConflictException.class)
    public ResponseEntity<WorkerApiError> manifestConflict(@NonNull WorkerManifestConflictException ex) {
        return error(
                HttpStatus.CONFLICT,
                "MANIFEST_CONFLICT",
                ex.getMessage()
        );
    }

    @ExceptionHandler(WorkerRenditionConflictException.class)
    public ResponseEntity<WorkerApiError> renditionConflict(@NonNull WorkerRenditionConflictException ex) {
        return error(
                HttpStatus.CONFLICT,
                "RENDITION_CONFLICT",
                ex.getMessage()
        );
    }

    @ExceptionHandler(WorkerPayloadTooLargeException.class)
    public ResponseEntity<WorkerApiError> manifestTooLarge(@NonNull WorkerPayloadTooLargeException ex) {
        return error(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "PAYLOAD_TOO_LARGE",
                ex.getMessage()
        );
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            ConstraintViolationException.class,
            MissingRequestHeaderException.class,
            MissingServletRequestPartException.class,
            IllegalArgumentException.class
    })
    public ResponseEntity<WorkerApiError> invalidInput(Exception ex) {
        return error(
                HttpStatus.BAD_REQUEST,
                "INVALID_REQUEST",
                "The worker request is invalid"
        );
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<WorkerApiError> tooLarge(MaxUploadSizeExceededException ex) {
        return error(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "PAYLOAD_TOO_LARGE",
                "The uploaded rendition exceeds the maximum size"
        );
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<WorkerApiError> unsupportedMedia(HttpMediaTypeNotSupportedException ex) {
        return error(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "UNSUPPORTED_MEDIA_TYPE",
                "The supplied media type is not supported"
        );
    }

    @ExceptionHandler(UnprocessableDocumentEvidenceException.class)
    public ResponseEntity<WorkerApiError> unusableEvidence(RuntimeException ex) {
        return error(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "UNUSABLE_EVIDENCE",
                "The supplied evidence cannot be processed"
        );
    }

    @ExceptionHandler(WorkerUnsupportedMediaTypeException.class)
    public ResponseEntity<WorkerApiError> unsupportedWorkerMedia(
            @NonNull WorkerUnsupportedMediaTypeException ex
    ) {
        return error(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "UNSUPPORTED_MEDIA_TYPE",
                ex.getMessage()
        );
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<WorkerApiError> routeNotFound(NoResourceFoundException ex) {
        return error(
                HttpStatus.NOT_FOUND,
                "WORKER_ROUTE_NOT_FOUND",
                "The worker endpoint does not exist"
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<WorkerApiError> unexpected(Exception ex) {
        return error(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                "The worker request could not be completed"
        );
    }

    private @NonNull ResponseEntity<WorkerApiError> error(
            HttpStatus status,
            String code,
            String message
    ) {
        return ResponseEntity.status(status).body(
                new WorkerApiError(
                        status.value(),
                        status.getReasonPhrase(),
                        code,
                        message
                )
        );
    }
}
