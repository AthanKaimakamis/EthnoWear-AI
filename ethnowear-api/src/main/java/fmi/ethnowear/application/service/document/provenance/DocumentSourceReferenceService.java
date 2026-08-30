package fmi.ethnowear.application.service.document.provenance;

import fmi.ethnowear.application.dto.document.command.provenance.DocumentDefaultSourceReferenceCommand;
import fmi.ethnowear.application.dto.document.query.DocumentSourceInheritanceDetails;
import fmi.ethnowear.application.exception.ResourceNotFoundException;
import fmi.ethnowear.application.model.event.ManagementEvent;
import fmi.ethnowear.application.service.document.review.DocumentPageChunkInvalidator;
import fmi.ethnowear.application.service.document.upload.DocumentPageProvenanceRecorder;
import fmi.ethnowear.application.service.document.upload.DocumentUploadReferenceResolver;
import fmi.ethnowear.application.service.event.ManagementEventPublisher;
import fmi.ethnowear.persistence.jpa.entity.SourceReference;
import fmi.ethnowear.persistence.jpa.entity.document.Document;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPage;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DocumentSourceReferenceService {

    private final DocumentRepository documentRepository;
    private final DocumentPageRepository pageRepository;
    private final DocumentUploadReferenceResolver referenceResolver;
    private final DocumentPageProvenanceRecorder provenanceRecorder;
    private final DocumentPageChunkInvalidator chunkInvalidator;
    private final ManagementEventPublisher managementEvents;

    @Transactional
    public DocumentSourceInheritanceDetails setDefault(
            Long documentId,
            DocumentDefaultSourceReferenceCommand command,
            String reviewer
    ) {
        validate(documentId, command, reviewer);

        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Document",
                        documentId
                ));
        SourceReference sourceReference = referenceResolver.sourceReference(
                command.sourceReferenceId()
        );
        referenceResolver.requireDocumentSource(document, sourceReference);

        document.setDefaultSourceReference(sourceReference);
        documentRepository.save(document);

        int inheritedPageCount = command.inheritToUnassignedPages()
                ? inheritUnassigned(
                        document,
                        sourceReference,
                        reviewer.trim(),
                        command.reason().trim()
                )
                : 0;

        managementEvents.document(document, ManagementEvent.Action.UPDATED);

        return new DocumentSourceInheritanceDetails(
                documentId,
                sourceReference.getId(),
                inheritedPageCount
        );
    }

    private int inheritUnassigned(
            Document document,
            SourceReference sourceReference,
            String reviewer,
            String reason
    ) {
        List<DocumentPage> pages = pageRepository
                .findByDocument_IdAndSourceReferenceIsNullOrderByPageSequenceAsc(
                        document.getId()
                );
        LocalDateTime reviewedAt = LocalDateTime.now(ZoneOffset.UTC);

        pages.forEach(page -> {
            page.setSourceReference(sourceReference);
            page.setProvenanceStatus(document.getProvenanceStatus());
            page.setProvenanceTrustState(document.getProvenanceTrustState());
            page.setProvenanceReviewedBy(reviewer);
            page.setProvenanceReviewedAt(reviewedAt);

            chunkInvalidator.invalidate(page);
            provenanceRecorder.recordInitial(
                    page,
                    sourceReference,
                    document.getProvenanceStatus(),
                    document.getProvenanceTrustState(),
                    reviewer,
                    reason
            );
            managementEvents.page(page, ManagementEvent.Action.STATUS_CHANGED);
        });

        pageRepository.saveAll(pages);

        return pages.size();
    }

    private void validate(
            Long documentId,
            DocumentDefaultSourceReferenceCommand command,
            String reviewer
    ) {
        if(documentId == null)
            throw new IllegalArgumentException("Document id is required");

        if(command == null || command.sourceReferenceId() == null)
            throw new IllegalArgumentException("Default source reference is required");

        if(command.reason() == null || command.reason().isBlank())
            throw new IllegalArgumentException("Source inheritance reason is required");

        if(reviewer == null || reviewer.isBlank())
            throw new IllegalArgumentException("Authenticated reviewer is required");
    }
}
