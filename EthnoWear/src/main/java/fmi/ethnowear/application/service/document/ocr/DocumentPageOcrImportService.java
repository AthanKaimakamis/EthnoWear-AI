package fmi.ethnowear.application.service.document.ocr;

import fmi.ethnowear.application.dto.document.command.ocr.OcrResultImportCommand;
import fmi.ethnowear.application.dto.document.query.history.DocumentPageOcrResultDetails;
import fmi.ethnowear.application.exception.ResourceNotFoundException;
import fmi.ethnowear.application.service.document.query.mapper.DocumentHistoryMapper;
import fmi.ethnowear.domain.model.document.indexing.IndexingState;
import fmi.ethnowear.domain.model.document.processing.JobType;
import fmi.ethnowear.domain.model.document.processing.ProcessingState;
import fmi.ethnowear.domain.model.document.review.ReviewState;
import fmi.ethnowear.domain.model.document.review.TranscriptionApprovalState;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPage;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPageMedia;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPageOcrResult;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentProcessingJob;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageMediaRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageOcrResultRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentProcessingJobRepository;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DocumentPageOcrImportService {

    private final DocumentPageRepository pageRepository;
    private final DocumentPageMediaRepository pageMediaRepository;
    private final DocumentPageOcrResultRepository ocrResultRepository;
    private final DocumentProcessingJobRepository processingJobRepository;
    private final OcrResultImportValidator validator;
    private final DocumentHistoryMapper historyMapper;

    @Transactional
    public DocumentPageOcrResultDetails importResult(
            Long pageId,
            OcrResultImportCommand command
    ) {
        if(pageId == null)
            throw new IllegalArgumentException("Document page id is required");

        validator.validate(command);

        DocumentPage page = pageRepository.findByIdForUpdate(pageId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Document page",
                        pageId
                ));

        DocumentPageMedia pageMedia = requirePageMedia(
                pageId,
                command.documentPageMediaId()
        );

        DocumentProcessingJob processingJob = requireProcessingJob(
                pageId,
                command.processingJobId()
        );

        deactivateCurrentResult(pageId);

        DocumentPageOcrResult result = createResult(
                page,
                pageMedia,
                processingJob,
                command
        );

        result = ocrResultRepository.saveAndFlush(result);

        updateCurrentSnapshot(page, command);
        pageRepository.save(page);

        return historyMapper.toDetails(result);
    }

    private DocumentPageMedia requirePageMedia(
            Long pageId,
            Long pageMediaId
    ) {
        return pageMediaRepository
                .findByIdAndDocumentPage_Id(pageMediaId, pageId)
                .orElseThrow(() -> new ResourceNotFoundException("Document page media",pageMediaId));
    }

    private DocumentProcessingJob requireProcessingJob(
            Long pageId,
            Long processingJobId
    ) {
        if(processingJobId == null)
            return null;

        DocumentProcessingJob job = processingJobRepository
                .findByIdAndDocumentPage_Id(processingJobId, pageId)
                .orElseThrow(() -> new ResourceNotFoundException("Document processing job",processingJobId));

        if(job.getJobType() != JobType.OCR)
            throw new IllegalArgumentException("The processing job must be an OCR job");

        return job;
    }

    private void deactivateCurrentResult(Long pageId) {
        ocrResultRepository
                .findByDocumentPage_IdAndCurrentTrue(pageId)
                .ifPresent(currentResult -> {
                    currentResult.setCurrent(false);
                    ocrResultRepository.saveAndFlush(currentResult);
                });
    }

    private DocumentPageOcrResult createResult(
            DocumentPage page,
            DocumentPageMedia pageMedia,
            DocumentProcessingJob processingJob,
            OcrResultImportCommand command
    ) {
        DocumentPageOcrResult result = new DocumentPageOcrResult();

        result.setDocumentPage(page);
        result.setDocumentPageMedia(pageMedia);
        result.setProcessingJob(processingJob);
        result.setRawText(command.rawText());
        result.setOcrEngine(command.ocrEngine().trim());
        result.setOcrEngineVersion(command.ocrEngineVersion());
        result.setOcrLanguage(command.ocrLanguage());
        result.setOcrConfidence(command.ocrConfidence());
        result.setParametersJson(command.parametersJson());
        result.setStructuredOutputJson(command.structuredOutputJson());
        result.setCurrent(true);

        return result;
    }

    private void updateCurrentSnapshot(
            @NonNull DocumentPage page,
            @NonNull OcrResultImportCommand command) {
        page.setRawOcrText(command.rawText());
        page.setOcrEngine(command.ocrEngine().trim());
        page.setOcrEngineVersion(command.ocrEngineVersion());
        page.setOcrLanguage(command.ocrLanguage());
        page.setOcrConfidence(command.ocrConfidence());
        page.setProcessingState(ProcessingState.COMPLETED);
        page.setReviewState(ReviewState.REVIEW_REQUIRED);
        page.setTranscriptionApprovalState(TranscriptionApprovalState.PENDING);
        page.setIndexingState(IndexingState.NOT_ELIGIBLE);
    }
}
