package fmi.ethnowear.application.service.document.processing;

import fmi.ethnowear.application.dto.document.query.history.DocumentProcessingJobDetails;
import fmi.ethnowear.application.exception.InvalidDocumentProcessingRequestException;
import fmi.ethnowear.application.exception.ResourceNotFoundException;
import fmi.ethnowear.application.service.document.query.mapper.DocumentHistoryMapper;
import fmi.ethnowear.domain.model.document.processing.ProcessingState;
import fmi.ethnowear.domain.model.document.review.TranscriptionApprovalState;
import fmi.ethnowear.persistence.jpa.entity.document.Document;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPage;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPageMedia;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentProcessingJob;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageMediaRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageOcrResultRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DocumentProcessingRequestService {

    private final DocumentRepository documentRepository;
    private final DocumentPageRepository pageRepository;
    private final DocumentPageMediaRepository pageMediaRepository;
    private final DocumentPageOcrResultRepository ocrResultRepository;
    private final DocumentProcessingJobScheduler jobScheduler;
    private final DocumentHistoryMapper historyMapper;

    @Transactional
    public DocumentProcessingJobDetails requestOcr(Long pageId) {
        return queueOcr(pageId);
    }

    @Transactional
    public DocumentProcessingJobDetails requestReprocessing(Long pageId) {
        return queueOcr(pageId);
    }

    @Transactional
    public DocumentProcessingJobDetails requestQualityAssessment(
            Long pageId
    ) {
        DocumentPage page = requirePage(pageId);

        if (ocrResultRepository
                .findByDocumentPage_IdAndCurrentTrue(pageId)
                .isEmpty())
            throw new InvalidDocumentProcessingRequestException(
                    "A current OCR result is required for quality assessment"
            );

        DocumentPageMedia input = requirePreferredOcrInput(pageId);

        return historyMapper.toDetails(
                jobScheduler.queueOcrQualityAssessment(
                        page,
                        input.getMediaAsset()
                )
        );
    }

    @Transactional
    public DocumentProcessingJobDetails requestChunkGeneration(Long documentId) {
        Document document = requireDocument(documentId);

        boolean hasApprovedPages = pageRepository
                .existsByDocument_IdAndTranscriptionApprovalState(
                        documentId,
                        TranscriptionApprovalState.APPROVED
                );

        if (!hasApprovedPages)
            throw new InvalidDocumentProcessingRequestException(
                    "At least one approved page is required for chunk generation"
            );

        return historyMapper.toDetails(
                jobScheduler.queueChunkGeneration(document)
        );
    }

    private DocumentProcessingJobDetails queueOcr(Long pageId) {
        DocumentPage page = requirePage(pageId);
        DocumentPageMedia input = requirePreferredOcrInput(pageId);

        DocumentProcessingJob job = jobScheduler.queueOcr(
                page,
                input.getMediaAsset()
        );

        page.setProcessingState(ProcessingState.PENDING);
        pageRepository.save(page);

        return historyMapper.toDetails(job);
    }

    private Document requireDocument(Long documentId) {
        if (documentId == null)
            throw new IllegalArgumentException("Document id is required");

        return documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document", documentId));
    }

    private DocumentPage requirePage(Long pageId) {
        if (pageId == null)
            throw new IllegalArgumentException("Document page id is required");

        return pageRepository.findById(pageId)
                .orElseThrow(() -> new ResourceNotFoundException("Document page", pageId));
    }

    private DocumentPageMedia requirePreferredOcrInput(Long pageId) {
        return pageMediaRepository
                .findByDocumentPage_IdAndPreferredOcrInputTrue(pageId)
                .orElseThrow(() ->
                        new InvalidDocumentProcessingRequestException("A preferred OCR input is required")
                );
    }
}
