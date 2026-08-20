package fmi.ethnowear.application.service.document.upload;

import fmi.ethnowear.application.dto.document.command.upload.*;
import fmi.ethnowear.application.exception.UnprocessableDocumentEvidenceException;
import fmi.ethnowear.domain.model.document.DocumentType;
import fmi.ethnowear.domain.model.document.provenance.ProvenanceStatus;
import fmi.ethnowear.domain.model.document.provenance.ProvenanceTrustState;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
@RequiredArgsConstructor
public class DocumentUploadValidator {

    private static final Set<DocumentType> PDF_TYPES = Set.of(
            DocumentType.PDF_DOCUMENT,
            DocumentType.SCANNED_BOOK
    );

    private final Validator validator;

    public void validate(PdfDocumentUploadCommand command) {
        validateCommand(command);

        if(!PDF_TYPES.contains(command.documentType()))
            throw new UnprocessableDocumentEvidenceException(
                    "Unsupported PDF document type: " + command.documentType()
            );

        validateProvenance(
                command.metadata().sourceId(),
                command.provenanceStatus(),
                command.provenanceTrustState()
        );
    }

    public void validate(StandaloneCaptureUploadCommand command) {
        validateCommand(command);
        validatePageProvenance(command.provenance());
        validatePrintedPageSort(command.printedPageSort());
    }

    public void validate(MissingPageUploadCommand command) {
        validateCommand(command);
        validatePageProvenance(command.provenance());
        validatePrintedPageSort(command.printedPageSort());
    }

    public void validate(ReplacementRenditionUploadCommand command) {
        validateCommand(command);
    }

    private void validatePageProvenance(PageProvenanceInput provenance) {
        validateProvenance(
                provenance.sourceReferenceId(),
                provenance.provenanceStatus(),
                provenance.provenanceTrustState()
        );
    }

    private void validateProvenance(
            Long sourceId,
            ProvenanceStatus status,
            ProvenanceTrustState trustState
    ) {
        if(status == ProvenanceStatus.KNOWN_SOURCE && sourceId == null)
            throw new UnprocessableDocumentEvidenceException(
                    "Known provenance requires a source"
            );

        if(status == ProvenanceStatus.UNKNOWN_SOURCE && sourceId != null)
            throw new UnprocessableDocumentEvidenceException(
                    "Unknown provenance cannot reference a source"
            );

        if(status == ProvenanceStatus.UNKNOWN_SOURCE
                && (trustState == ProvenanceTrustState.TRUSTED
                || trustState == ProvenanceTrustState.VERIFIED))
            throw new UnprocessableDocumentEvidenceException(
                    "Unknown provenance cannot be trusted or verified"
            );
    }

    private void validatePrintedPageSort(Integer printedPageSort) {
        if(printedPageSort != null && printedPageSort < 0)
            throw new IllegalArgumentException(
                    "Printed page sort cannot be negative"
            );
    }

    private <T> void validateCommand(T command) {
        if(command == null)
            throw new IllegalArgumentException(
                    "Document upload command is required"
            );

        var violations = validator.validate(command);

        if(!violations.isEmpty())
            throw new ConstraintViolationException(violations);
    }
}