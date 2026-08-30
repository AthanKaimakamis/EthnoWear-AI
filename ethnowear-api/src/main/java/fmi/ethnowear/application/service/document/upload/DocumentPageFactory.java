package fmi.ethnowear.application.service.document.upload;

import fmi.ethnowear.application.dto.document.command.upload.MissingPageUploadCommand;
import fmi.ethnowear.application.dto.document.command.upload.PageProvenanceInput;
import fmi.ethnowear.application.dto.document.command.upload.StandaloneCaptureUploadCommand;
import fmi.ethnowear.domain.model.document.EvidenceState;
import fmi.ethnowear.domain.model.document.PageKind;
import fmi.ethnowear.domain.model.document.PageRole;
import fmi.ethnowear.domain.model.document.indexing.IndexingState;
import fmi.ethnowear.domain.model.document.processing.ProcessingState;
import fmi.ethnowear.domain.model.document.review.ReviewState;
import fmi.ethnowear.domain.model.document.review.TranscriptionApprovalState;
import fmi.ethnowear.persistence.jpa.entity.SourceReference;
import fmi.ethnowear.persistence.jpa.entity.document.Document;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPage;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

@Component
public class DocumentPageFactory {

    public @NonNull DocumentPage createStandalone(
            @NonNull Document document,
            SourceReference sourceReference,
            @NonNull StandaloneCaptureUploadCommand command
    ) {
        return create(
                document,
                sourceReference,
                command.provenance(),
                PageKind.STANDALONE_IMAGE,
                PageRole.NORMAL,
                1,
                command.printedPageNumber(),
                command.printedPageSort(),
                command.pageLabel()
        );
    }

    public @NonNull DocumentPage createMissingPage(
            @NonNull Document document,
            SourceReference sourceReference,
            @NonNull MissingPageUploadCommand command
    ) {
        return create(
                document,
                sourceReference,
                command.provenance(),
                PageKind.DOCUMENT_PAGE,
                PageRole.MISSING_PAGE,
                command.pageSequence(),
                command.printedPageNumber(),
                command.printedPageSort(),
                command.pageLabel()
        );
    }

    private @NonNull DocumentPage create(
            Document document,
            SourceReference sourceReference,
            @NonNull PageProvenanceInput provenance,
            PageKind pageKind,
            PageRole pageRole,
            int pageSequence,
            String printedPageNumber,
            Integer printedPageSort,
            String pageLabel
    ) {
        DocumentPage page = new DocumentPage();

        page.setDocument(document);
        page.setSourceReference(sourceReference);
        page.setPageKind(pageKind);
        page.setPageRole(pageRole);
        page.setPageSequence(pageSequence);
        page.setPdfPageIndex(null);
        page.setPrintedPageNumber(printedPageNumber);
        page.setPrintedPageSort(printedPageSort);
        page.setPageLabel(pageLabel);

        page.setProvenanceStatus(provenance.provenanceStatus());
        page.setProvenanceTrustState(provenance.provenanceTrustState());
        page.setProvenanceNote(provenance.note());
        page.setProvenanceReviewedBy(provenance.recordedBy());

        page.setProcessingState(ProcessingState.PENDING);
        page.setReviewState(ReviewState.NOT_READY);
        page.setTranscriptionApprovalState(TranscriptionApprovalState.PENDING);
        page.setIndexingState(IndexingState.NOT_ELIGIBLE);
        page.setEvidenceState(EvidenceState.ACTIVE);

        return page;
    }
}
