package fmi.ethnowear.api.exception;

import fmi.ethnowear.application.dto.worker.WorkerApiError;
import fmi.ethnowear.application.exception.WorkerPayloadTooLargeException;
import fmi.ethnowear.application.exception.WorkerPreferredOcrInputConflictException;
import fmi.ethnowear.application.exception.WorkerNotReadyException;
import fmi.ethnowear.application.exception.WorkerUnsupportedMediaTypeException;
import fmi.ethnowear.application.exception.WorkerQualityAssessmentConflictException;
import fmi.ethnowear.application.exception.WorkerVisionValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorkerApiExceptionHandlerTest {

    private final WorkerApiExceptionHandler handler = new WorkerApiExceptionHandler();

    @Test
    void mapsOversizedWorkerPayloadTo413() {
        ResponseEntity<WorkerApiError> response = handler.manifestTooLarge(
                new WorkerPayloadTooLargeException("Manifest exceeds the maximum page count")
        );

        assertEquals(413, response.getStatusCode().value());
        assertEquals("PAYLOAD_TOO_LARGE", response.getBody().code());
    }

    @Test
    void mapsUnsupportedWorkerMediaTo415() {
        ResponseEntity<WorkerApiError> response = handler.unsupportedWorkerMedia(
                new WorkerUnsupportedMediaTypeException("Unsupported rendition media type")
        );

        assertEquals(415, response.getStatusCode().value());
        assertEquals("UNSUPPORTED_MEDIA_TYPE", response.getBody().code());
    }

    @Test
    void mapsUnavailableWorkerDependencyTo503() {
        ResponseEntity<WorkerApiError> response = handler.notReady(
                new WorkerNotReadyException("Database is unavailable")
        );

        assertEquals(503, response.getStatusCode().value());
        assertEquals("WORKER_API_NOT_READY", response.getBody().code());
    }

    @Test
    void mapsConflictingQualityAssessmentTo409() {
        ResponseEntity<WorkerApiError> response = handler.qualityAssessmentConflict(
                new WorkerQualityAssessmentConflictException()
        );

        assertEquals(409, response.getStatusCode().value());
        assertEquals("QUALITY_ASSESSMENT_CONFLICT", response.getBody().code());
    }

    @Test
    void preservesPreciseSafeVisionValidationCode() {
        ResponseEntity<WorkerApiError> response = handler.invalidVisionResult(
                new WorkerVisionValidationException(
                        "VISION_ISSUE_TEXT_MISMATCH",
                        "Issue text does not match the assessed OCR snapshot"
                )
        );

        assertEquals(400, response.getStatusCode().value());
        assertEquals("VISION_ISSUE_TEXT_MISMATCH", response.getBody().code());
        assertEquals(
                "Issue text does not match the assessed OCR snapshot",
                response.getBody().message()
        );
    }

    @Test
    void mapsPreferredOcrInputConstraintConflictTo409() {
        ResponseEntity<WorkerApiError> response = handler.preferredOcrInputConflict(
                new WorkerPreferredOcrInputConflictException()
        );

        assertEquals(409, response.getStatusCode().value());
        assertEquals("PREFERRED_OCR_INPUT_CONFLICT", response.getBody().code());
    }

    @Test
    void doesNotExposeUnexpectedExceptionDetails() {
        ResponseEntity<WorkerApiError> response = handler.unexpected(
                new IllegalStateException("/private/storage/secret.pdf")
        );

        assertEquals(500, response.getStatusCode().value());
        assertEquals("INTERNAL_ERROR", response.getBody().code());
        assertEquals("The worker request could not be completed", response.getBody().message());
    }
}
