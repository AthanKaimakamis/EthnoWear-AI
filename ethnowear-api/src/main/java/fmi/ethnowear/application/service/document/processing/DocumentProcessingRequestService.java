package fmi.ethnowear.application.service.document.processing;

import fmi.ethnowear.application.dto.document.query.history.DocumentProcessingJobDetails;
import fmi.ethnowear.application.exception.InvalidDocumentProcessingRequestException;
import fmi.ethnowear.application.exception.ChunkGenerationIneligibleException;
import fmi.ethnowear.application.exception.ResourceNotFoundException;
import fmi.ethnowear.application.model.event.ManagementEvent;
import fmi.ethnowear.application.service.document.query.mapper.DocumentHistoryMapper;
import fmi.ethnowear.application.service.document.chunk.DocumentChunkGenerationEligibilityService;
import fmi.ethnowear.application.service.event.ManagementEventPublisher;
import fmi.ethnowear.domain.model.document.processing.ProcessingState;
import fmi.ethnowear.persistence.jpa.entity.document.Document;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPage;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPageMedia;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentProcessingJob;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageMediaRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageOcrResultRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageQualityAssessmentRepository;
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
    private final DocumentPageQualityAssessmentRepository assessmentRepository;
    private final DocumentProcessingJobScheduler jobScheduler;
    private final DocumentChunkGenerationEligibilityService chunkEligibility;
    private final DocumentProcessingStateReconciler processingStateReconciler;
    private final DocumentHistoryMapper historyMapper;
    private final ManagementEventPublisher managementEvents;

    @Transactional
    public DocumentProcessingJobDetails requestOcr(Long pageId) {
        return queueOcr(pageId);
    }

    @Transactional
    public DocumentProcessingJobDetails requestReprocessing(Long pageId) {
        return queueOcr(pageId);
    }

    @Transactional
    public DocumentProcessingJobDetails createOcrJob(Long pageId) {
        DocumentPage page = requirePage(pageId);
        DocumentPageMedia input = requirePreferredOcrInput(pageId);

        DocumentProcessingJob job = jobScheduler.createNewOcrJob(
                page,
                input.getMediaAsset()
        );

        markPending(page);
        return historyMapper.toDetails(job);
    }

    @Transactional
    public DocumentProcessingJobDetails requestQualityAssessment(
            Long pageId
    ) {
        DocumentPage page = requirePage(pageId);

        var ocrResult = ocrResultRepository
                .findByDocumentPage_IdAndCurrentTrue(pageId)
                .orElseThrow(() -> new InvalidDocumentProcessingRequestException(
                        "A current OCR result is required for quality assessment"
                ));

        DocumentPageMedia input = requirePreferredOcrInput(pageId);

        return historyMapper.toDetails(
                jobScheduler.queueOcrQualityAssessment(
                        page,
                        input.getMediaAsset(),
                        ocrResult
                )
        );
    }

    @Transactional
    public DocumentProcessingJobDetails requestVisionAssessment(Long pageId) {
        DocumentPage page = requirePageForUpdate(pageId);
        var ocrResult = ocrResultRepository
                .findByDocumentPage_IdAndCurrentTrue(pageId)
                .orElseThrow(() -> new InvalidDocumentProcessingRequestException(
                        "A current OCR result is required for vision assessment"
                ));
        DocumentPageMedia input = ocrResult.getDocumentPageMedia();

        if (input == null || input.getMediaAsset() == null)
            throw new InvalidDocumentProcessingRequestException(
                    "The current OCR result has no image input"
            );

        var deterministicAssessment = assessmentRepository
                .findByDocumentPage_IdAndDocumentPageMedia_IdAndAssessmentTypeAndCurrentTrue(
                        pageId,
                        input.getId(),
                        fmi.ethnowear.domain.model.document.quality.AssessmentType.COMBINED_OCR_QUALITY
                )
                .filter(assessment -> assessment.getDocumentPageOcrResult() != null)
                .filter(assessment -> ocrResult.getId().equals(
                        assessment.getDocumentPageOcrResult().getId()
                ))
                .orElseThrow(() -> new InvalidDocumentProcessingRequestException(
                        "A current deterministic assessment is required for vision assessment"
                ));

        DocumentProcessingJob job = jobScheduler.queueVisionOcrAssessment(
                page,
                input.getMediaAsset(),
                ocrResult,
                deterministicAssessment
        );

        return historyMapper.toDetails(job);
    }

    @Transactional
    public DocumentProcessingJobDetails requestChunkGeneration(Long documentId) {
        Document document = requireDocument(documentId);
        var input = chunkEligibility.requireInput(documentId);

        if (input.pages().isEmpty())
            throw new ChunkGenerationIneligibleException(input.blockers());

        return historyMapper.toDetails(
                jobScheduler.queueChunkGeneration(
                        document,
                        input.generationInputHash()
                )
        );
    }

    private DocumentProcessingJobDetails queueOcr(Long pageId) {
        DocumentPage page = requirePage(pageId);
        DocumentPageMedia input = requirePreferredOcrInput(pageId);

        DocumentProcessingJob job = jobScheduler.queueOcr(
                page,
                input.getMediaAsset()
        );

        markPending(page);

        return historyMapper.toDetails(job);
    }

    private void markPending(DocumentPage page) {
        page.setProcessingState(ProcessingState.PENDING);
        pageRepository.saveAndFlush(page);
        processingStateReconciler.reconcile(page.getDocument().getId());
        managementEvents.page(page, ManagementEvent.Action.STATUS_CHANGED);
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

    private DocumentPage requirePageForUpdate(Long pageId) {
        if (pageId == null)
            throw new IllegalArgumentException("Document page id is required");

        return pageRepository.findByIdForUpdate(pageId)
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
