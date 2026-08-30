package fmi.ethnowear.api.exception;

import fmi.ethnowear.application.exception.ActiveDocumentJobExistsException;
import fmi.ethnowear.application.exception.VisionJobAlreadyActiveException;
import fmi.ethnowear.application.exception.InvalidDocumentProcessingRequestException;
import fmi.ethnowear.application.exception.UnprocessableDocumentEvidenceException;
import fmi.ethnowear.application.exception.InvalidDocumentJobTransitionException;
import fmi.ethnowear.application.exception.ResourceNotFoundException;
import fmi.ethnowear.application.exception.ChunkGenerationIneligibleException;
import fmi.ethnowear.application.dto.document.query.chunk.ChunkGenerationBlockerDetails;
import fmi.ethnowear.domain.model.document.processing.JobType;
import fmi.ethnowear.domain.model.document.processing.JobStatus;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.util.List;

class DocumentApiExceptionHandlerTest {

    private final DocumentApiExceptionHandler handler = new DocumentApiExceptionHandler();

    @Test
    void mapsInvalidProcessingRequestsToUnprocessableEntity() {
        var response = handler.unprocessable(
                new InvalidDocumentProcessingRequestException("OCR input is required")
        );

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatusCode());
        assertEquals(422, response.getBody().get("status"));
    }

    @Test
    void mapsUnusableEvidenceToUnprocessableEntity() {
        var response = handler.unprocessable(
                new UnprocessableDocumentEvidenceException("Invalid PDF")
        );

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatusCode());
        assertEquals("Invalid PDF", response.getBody().get("message"));
    }

    @Test
    void keepsActiveJobCollisionsAsConflicts() {
        var response = handler.conflict(new ActiveDocumentJobExistsException(JobType.OCR));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals(
                "PROCESSING_JOB_ACTIVE_CONFLICT",
                response.getBody().get("code")
        );
    }

    @Test
    void returnsVisionSpecificConflictWithSafeActiveJob() {
        var response = handler.visionJobAlreadyActive(
                new VisionJobAlreadyActiveException(
                        51L,
                        JobStatus.RUNNING,
                        8L,
                        21L
                )
        );

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("VISION_JOB_ALREADY_ACTIVE", response.getBody().get("code"));
        var activeJob = (java.util.Map<?, ?>) response.getBody().get("activeJob");
        assertEquals(51L, activeJob.get("id"));
        assertEquals(JobStatus.RUNNING, activeJob.get("status"));
        assertEquals(8L, activeJob.get("documentPageId"));
        assertEquals(21L, activeJob.get("ocrResultId"));
    }

    @Test
    void returnsStableProcessingJobErrorCodes() {
        var notFound = handler.notFound(
                new ResourceNotFoundException("Document processing job", 42L)
        );
        var transition = handler.conflict(
                new InvalidDocumentJobTransitionException("Cannot retry")
        );

        assertEquals("PROCESSING_JOB_NOT_FOUND", notFound.getBody().get("code"));
        assertEquals(
                "PROCESSING_JOB_INVALID_TRANSITION",
                transition.getBody().get("code")
        );
    }

    @Test
    void returnsStructuredChunkGenerationBlockers() {
        var response = handler.chunkGenerationIneligible(
                new ChunkGenerationIneligibleException(List.of(
                        new ChunkGenerationBlockerDetails(
                                12L,
                                "TRANSCRIPTION_NOT_APPROVED",
                                "Corrected transcription is not approved"
                        )
                ))
        );

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatusCode());
        assertEquals(
                "CHUNK_GENERATION_INELIGIBLE",
                response.getBody().get("code")
        );
        assertEquals(1, ((List<?>) response.getBody().get("blockers")).size());
    }
}
