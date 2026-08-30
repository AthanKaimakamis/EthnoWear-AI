package fmi.ethnowear.application.service.document.retention;

import fmi.ethnowear.application.dto.document.query.retention.MediaCleanupEligibilityDetails;
import fmi.ethnowear.application.exception.ResourceNotFoundException;
import fmi.ethnowear.domain.model.document.indexing.IndexingState;
import fmi.ethnowear.domain.model.document.processing.JobType;
import fmi.ethnowear.domain.model.document.processing.ProcessingState;
import fmi.ethnowear.domain.model.document.review.TranscriptionApprovalState;
import fmi.ethnowear.domain.model.media.MediaOrigin;
import fmi.ethnowear.domain.model.media.MediaRetentionPolicy;
import fmi.ethnowear.persistence.jpa.entity.document.Document;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPage;
import fmi.ethnowear.persistence.jpa.repository.KnowledgeChunkRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageMediaRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentProcessingJobRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

import static fmi.ethnowear.util.IdentifierUtils.requireId;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MediaCleanupEligibilityService {

    private final DocumentRepository documentRepository;
    private final DocumentPageRepository pageRepository;
    private final DocumentPageMediaRepository pageMediaRepository;
    private final DocumentProcessingJobRepository jobRepository;
    private final KnowledgeChunkRepository chunkRepository;
    private final GeneratedDocumentMediaPolicy generatedMediaPolicy;

    public MediaCleanupEligibilityDetails evaluate(Long documentId) {
        requireId(documentId, "Document");

        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Document",
                        documentId
                ));

        return evaluate(document, true);
    }

    public MediaCleanupEligibilityDetails evaluate(
            Document document,
            boolean requireGeneratedMedia
    ) {
        List<String> blockers = new ArrayList<>();
        List<DocumentPage> pages = pageRepository
                .findByDocument_IdOrderByPageSequenceAsc(document.getId());

        if (document.getProcessingState() != ProcessingState.COMPLETED)
            blockers.add("Document processing is not complete");

        if (document.getIndexingState() != IndexingState.INDEXED)
            blockers.add("Document indexing has not succeeded");

        if (pages.isEmpty())
            blockers.add("Document has no active pages");

        if (pages.stream().anyMatch(page ->
                page.getProcessingState() != ProcessingState.COMPLETED))
            blockers.add("One or more pages are not fully processed");

        if (pages.stream().anyMatch(this::requiresApproval))
            blockers.add("One or more required pages are not approved");

        if (chunkRepository.countCurrentEligibleByDocumentId(
                document.getId()
        ) == 0)
            blockers.add("Document has no knowledge chunks");
        else if (chunkRepository
                .countCurrentEligibleByDocumentIdAndIndexingStateNot(
                document.getId(),
                IndexingState.INDEXED
        ) > 0)
            blockers.add("One or more document chunks are not indexed");

        if (jobRepository.existsActiveDocumentJobOtherThan(
                document.getId(),
                JobType.MEDIA_CLEANUP
        ))
            blockers.add("Document has active processing jobs");

        int generatedMediaCount = pageMediaRepository.findCleanupCandidates(
                document.getId(),
                generatedMediaPolicy.renditionTypes(),
                MediaOrigin.GENERATED,
                null
        ).size();

        if (requireGeneratedMedia && generatedMediaCount == 0)
            blockers.add("Document has no generated media eligible for cleanup");

        return new MediaCleanupEligibilityDetails(
                document.getId(),
                blockers.isEmpty(),
                MediaRetentionPolicy.KEEP_ORIGINAL_ONLY,
                generatedMediaCount,
                blockers
        );
    }

    private boolean requiresApproval(DocumentPage page) {
        return page.getTranscriptionApprovalState()
                != TranscriptionApprovalState.APPROVED
                && page.getTranscriptionApprovalState()
                != TranscriptionApprovalState.NOT_REQUIRED;
    }
}
