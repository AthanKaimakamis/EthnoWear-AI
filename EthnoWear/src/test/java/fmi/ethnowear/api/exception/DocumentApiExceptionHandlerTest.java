package fmi.ethnowear.api.exception;

import fmi.ethnowear.application.exception.ActiveDocumentJobExistsException;
import fmi.ethnowear.application.exception.InvalidDocumentProcessingRequestException;
import fmi.ethnowear.application.exception.UnprocessableDocumentEvidenceException;
import fmi.ethnowear.domain.model.document.processing.JobType;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
    }
}
