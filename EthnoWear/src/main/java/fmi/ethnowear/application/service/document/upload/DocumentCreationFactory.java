package fmi.ethnowear.application.service.document.upload;

import fmi.ethnowear.application.dto.document.command.upload.DocumentBibliographicInput;
import fmi.ethnowear.domain.model.document.DocumentType;
import fmi.ethnowear.domain.model.document.indexing.IndexingState;
import fmi.ethnowear.domain.model.document.processing.ProcessingState;
import fmi.ethnowear.domain.model.document.provenance.ProvenanceStatus;
import fmi.ethnowear.domain.model.document.provenance.ProvenanceTrustState;
import fmi.ethnowear.domain.model.document.review.ReviewState;
import fmi.ethnowear.persistence.jpa.entity.Source;
import fmi.ethnowear.persistence.jpa.entity.document.Document;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

@Component
public class DocumentCreationFactory {

    public @NonNull Document create(
            @NonNull DocumentBibliographicInput metadata,
            @NonNull DocumentType documentType,
            @NonNull ProvenanceStatus provenanceStatus,
            @NonNull ProvenanceTrustState provenanceTrustState,
            Source source
    ) {
        Document document = new Document();

        document.setSource(source);
        document.setDocumentType(documentType);
        document.setProvenanceStatus(provenanceStatus);
        document.setProvenanceTrustState(provenanceTrustState);

        document.setTitle(metadata.title());
        document.setAuthor(metadata.author());
        document.setPublisher(metadata.publisher());
        document.setPublicationYear(metadata.publicationYear());
        document.setLanguage(metadata.language());
        document.setNotes(metadata.notes());

        document.setPageCount(null);
        document.setProcessingState(ProcessingState.UPLOADED);
        document.setReviewState(ReviewState.NOT_READY);
        document.setIndexingState(IndexingState.NOT_ELIGIBLE);

        return document;
    }
}
