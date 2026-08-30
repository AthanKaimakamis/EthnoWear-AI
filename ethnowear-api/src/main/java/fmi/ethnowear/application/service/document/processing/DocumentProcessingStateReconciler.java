package fmi.ethnowear.application.service.document.processing;

import fmi.ethnowear.application.exception.ResourceNotFoundException;
import fmi.ethnowear.application.model.event.ManagementEvent;
import fmi.ethnowear.application.service.event.ManagementEventPublisher;
import fmi.ethnowear.domain.model.document.processing.ProcessingState;
import fmi.ethnowear.persistence.jpa.entity.document.Document;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPage;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static fmi.ethnowear.util.IdentifierUtils.requireId;

@Service
@RequiredArgsConstructor
public class DocumentProcessingStateReconciler {

    private final DocumentRepository documentRepository;
    private final DocumentPageRepository pageRepository;
    private final ManagementEventPublisher managementEvents;

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean reconcile(Long documentId) {
        requireId(documentId, "Document");

        Document document = documentRepository.findByIdForUpdate(documentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Document",
                        documentId
                ));
        List<DocumentPage> pages = pageRepository
                .findActiveByDocumentIdOrderByPageSequence(documentId);

        if (pages.isEmpty())
            return false;

        ProcessingState state = aggregate(pages);

        if (document.getProcessingState() == state)
            return false;

        document.setProcessingState(state);
        documentRepository.saveAndFlush(document);
        managementEvents.document(
                document,
                ManagementEvent.Action.STATUS_CHANGED
        );
        return true;
    }

    private ProcessingState aggregate(List<DocumentPage> pages) {
        if (pages.stream().anyMatch(page ->
                page.getProcessingState() == ProcessingState.FAILED))
            return ProcessingState.FAILED;

        if (pages.stream().anyMatch(page ->
                page.getProcessingState() == ProcessingState.CANCELLED))
            return ProcessingState.CANCELLED;

        if (pages.stream().allMatch(page ->
                page.getProcessingState() == ProcessingState.COMPLETED))
            return ProcessingState.COMPLETED;

        return ProcessingState.PROCESSING;
    }
}
