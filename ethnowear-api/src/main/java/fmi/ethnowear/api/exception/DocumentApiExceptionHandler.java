package fmi.ethnowear.api.exception;

import fmi.ethnowear.application.exception.*;
import jakarta.validation.ConstraintViolationException;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.Map;
import java.util.LinkedHashMap;

@RestControllerAdvice(basePackages = "fmi.ethnowear.api.controller.document")
public class DocumentApiExceptionHandler extends BaseExceptionHandler {

    @ExceptionHandler(VisionJobAlreadyActiveException.class)
    public ResponseEntity<Map<String, Object>> visionJobAlreadyActive(
            @NonNull VisionJobAlreadyActiveException ex
    ) {
        Map<String, Object> activeJob = new LinkedHashMap<>();
        activeJob.put("id", ex.getActiveJobId());
        activeJob.put("status", ex.getActiveJobStatus());
        activeJob.put("documentPageId", ex.getDocumentPageId());
        activeJob.put("ocrResultId", ex.getOcrResultId());

        return error(
                HttpStatus.CONFLICT,
                ex.getMessage(),
                Map.of(
                        "code", "VISION_JOB_ALREADY_ACTIVE",
                        "activeJob", activeJob
                )
        );
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> notFound(
            @NonNull ResourceNotFoundException ex
    ) {
        String code = "Document processing job".equals(ex.getResourceType())
                ? "PROCESSING_JOB_NOT_FOUND"
                : "DOCUMENT_RESOURCE_NOT_FOUND";
        return error(HttpStatus.NOT_FOUND, code, ex.getMessage());
    }

    @ExceptionHandler({
            InvalidDocumentJobTransitionException.class,
            InvalidDocumentPageReviewTransitionException.class,
            InvalidDocumentPageProvenanceTransitionException.class,
            ActiveDocumentJobExistsException.class,
            DocumentDependencyConflictException.class,
            fmi.ethnowear.util.RowVersionUtils.StaleRowVersionException.class
    })
    public ResponseEntity<Map<String, Object>> conflict(
            @NonNull RuntimeException ex
    ) {
        String code = ex instanceof ActiveDocumentJobExistsException
                ? "PROCESSING_JOB_ACTIVE_CONFLICT"
                : ex instanceof DocumentDependencyConflictException
                        ? "DOCUMENT_DEPENDENCY_CONFLICT"
                : ex instanceof fmi.ethnowear.util.RowVersionUtils.StaleRowVersionException
                        ? "STALE_RESOURCE_VERSION"
                : ex instanceof InvalidDocumentJobTransitionException
                        ? "PROCESSING_JOB_INVALID_TRANSITION"
                        : "DOCUMENT_STATE_CONFLICT";
        return error(HttpStatus.CONFLICT, code, ex.getMessage());
    }

    @ExceptionHandler({
            InvalidDocumentProcessingRequestException.class,
            UnprocessableDocumentEvidenceException.class
    })
    public ResponseEntity<Map<String, Object>> unprocessable(
            @NonNull RuntimeException ex
    ) {
        return error(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "DOCUMENT_EVIDENCE_INVALID",
                ex.getMessage()
        );
    }

    @ExceptionHandler(ChunkGenerationIneligibleException.class)
    public ResponseEntity<Map<String, Object>> chunkGenerationIneligible(
            @NonNull ChunkGenerationIneligibleException ex
    ) {
        return error(
                HttpStatus.UNPROCESSABLE_ENTITY,
                ex.getMessage(),
                Map.of(
                        "code", "CHUNK_GENERATION_INELIGIBLE",
                        "blockers", ex.getBlockers()
                )
        );
    }

    @ExceptionHandler({
            IllegalArgumentException.class
    })
    public ResponseEntity<Map<String, Object>> invalidInput(
            @NonNull RuntimeException ex
    ) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", ex.getMessage());
    }

    @ExceptionHandler(RetrievalUnavailableException.class)
    public ResponseEntity<Map<String, Object>> vectorCleanupUnavailable(
            @NonNull RetrievalUnavailableException ex
    ) {
        return error(
                HttpStatus.SERVICE_UNAVAILABLE,
                "DOCUMENT_VECTOR_CLEANUP_UNAVAILABLE",
                ex.getMessage()
        );
    }

    @Override
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> validation(
            @NonNull MethodArgumentNotValidException ex
    ) {
        return error(
                HttpStatus.BAD_REQUEST,
                "Validation failed",
                Map.of(
                        "code", "VALIDATION_FAILED",
                        "fields", validationFields(ex)
                )
        );
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> constraintViolation(
            @NonNull ConstraintViolationException ex
    ) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("error", HttpStatus.BAD_REQUEST.getReasonPhrase());
        body.put("message", "Validation failed");
        body.put("code", "VALIDATION_FAILED");

        Map<String, String> fields = new LinkedHashMap<>();
        ex.getConstraintViolations().forEach(violation -> fields.put(
                violation.getPropertyPath().toString(),
                violation.getMessage()
        ));
        body.put("fields", fields);

        return ResponseEntity.badRequest().body(body);
    }
}
