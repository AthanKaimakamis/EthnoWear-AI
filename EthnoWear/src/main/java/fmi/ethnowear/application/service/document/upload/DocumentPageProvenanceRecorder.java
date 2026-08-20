package fmi.ethnowear.application.service.document.upload;

import fmi.ethnowear.application.dto.document.command.upload.PageProvenanceInput;
import fmi.ethnowear.domain.model.document.provenance.ProvenanceEventType;
import fmi.ethnowear.persistence.jpa.entity.SourceReference;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPage;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPageProvenanceEvent;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageProvenanceEventRepository;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class DocumentPageProvenanceRecorder {

    private final DocumentPageProvenanceEventRepository repository;

    @Transactional(propagation = Propagation.MANDATORY)
    public @NonNull DocumentPageProvenanceEvent recordInitial(
            @NonNull DocumentPage page,
            SourceReference sourceReference,
            @NonNull PageProvenanceInput provenance
    ) {
        DocumentPageProvenanceEvent event = new DocumentPageProvenanceEvent();

        event.setDocumentPage(page);
        event.setEventType(ProvenanceEventType.SOURCE_IDENTIFIED);
        event.setPreviousSourceReference(null);
        event.setNewSourceReference(sourceReference);
        event.setPreviousProvenanceStatus(null);
        event.setNewProvenanceStatus(provenance.provenanceStatus());
        event.setPreviousTrustState(null);
        event.setNewTrustState(provenance.provenanceTrustState());
        event.setPreviousCanonicalDocumentPage(null);
        event.setNewCanonicalDocumentPage(null);
        event.setReviewedBy(provenance.recordedBy());
        event.setReason(provenance.reason());

        return repository.save(event);
    }
}
