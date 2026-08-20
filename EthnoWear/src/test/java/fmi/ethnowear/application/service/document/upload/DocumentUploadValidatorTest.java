package fmi.ethnowear.application.service.document.upload;

import fmi.ethnowear.application.dto.document.command.upload.DocumentBibliographicInput;
import fmi.ethnowear.application.dto.document.command.upload.PdfDocumentUploadCommand;
import fmi.ethnowear.application.exception.UnprocessableDocumentEvidenceException;
import fmi.ethnowear.domain.model.document.DocumentType;
import fmi.ethnowear.domain.model.document.provenance.ProvenanceStatus;
import fmi.ethnowear.domain.model.document.provenance.ProvenanceTrustState;
import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DocumentUploadValidatorTest {

    private DocumentUploadValidator validator;

    @BeforeEach
    void setUp() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            validator = new DocumentUploadValidator(factory.getValidator());
        }
    }

    @Test
    void acceptsSupportedPdfWithConsistentKnownProvenance() {
        assertDoesNotThrow(() -> validator.validate(command(
                7L,
                DocumentType.PDF_DOCUMENT,
                ProvenanceStatus.KNOWN_SOURCE,
                ProvenanceTrustState.VERIFIED
        )));
    }

    @Test
    void rejectsUnsupportedPdfDocumentTypeAsUnprocessable() {
        assertThrows(
                UnprocessableDocumentEvidenceException.class,
                () -> validator.validate(command(
                        7L,
                        DocumentType.STANDALONE_CAPTURE,
                        ProvenanceStatus.KNOWN_SOURCE,
                        ProvenanceTrustState.TRUSTED
                ))
        );
    }

    @Test
    void rejectsKnownProvenanceWithoutSourceAsUnprocessable() {
        assertThrows(
                UnprocessableDocumentEvidenceException.class,
                () -> validator.validate(command(
                        null,
                        DocumentType.SCANNED_BOOK,
                        ProvenanceStatus.KNOWN_SOURCE,
                        ProvenanceTrustState.PARTIAL
                ))
        );
    }

    @Test
    void rejectsTrustedUnknownProvenanceAsUnprocessable() {
        assertThrows(
                UnprocessableDocumentEvidenceException.class,
                () -> validator.validate(command(
                        null,
                        DocumentType.PDF_DOCUMENT,
                        ProvenanceStatus.UNKNOWN_SOURCE,
                        ProvenanceTrustState.TRUSTED
                ))
        );
    }

    private PdfDocumentUploadCommand command(
            Long sourceId,
            DocumentType documentType,
            ProvenanceStatus provenanceStatus,
            ProvenanceTrustState trustState
    ) {
        return new PdfDocumentUploadCommand(
                new DocumentBibliographicInput(
                        sourceId,
                        "Document title",
                        null,
                        null,
                        null,
                        "bg",
                        null
                ),
                documentType,
                provenanceStatus,
                trustState,
                null
        );
    }
}
