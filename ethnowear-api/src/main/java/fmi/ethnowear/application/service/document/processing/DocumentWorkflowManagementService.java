package fmi.ethnowear.application.service.document.processing;

import fmi.ethnowear.application.dto.document.query.DocumentPageDetails;
import fmi.ethnowear.application.dto.document.query.history.DocumentProcessingJobDetails;
import fmi.ethnowear.application.exception.ActiveDocumentJobExistsException;
import fmi.ethnowear.application.exception.DocumentDependencyConflictException;
import fmi.ethnowear.application.exception.InvalidDocumentJobTransitionException;
import fmi.ethnowear.application.exception.InvalidDocumentProcessingRequestException;
import fmi.ethnowear.application.exception.ResourceNotFoundException;
import fmi.ethnowear.application.model.event.ManagementEvent;
import fmi.ethnowear.application.service.document.query.mapper.DocumentHistoryMapper;
import fmi.ethnowear.application.service.document.query.mapper.DocumentPageQueryMapper;
import fmi.ethnowear.application.service.event.ManagementEventPublisher;
import fmi.ethnowear.application.service.document.figure.DocumentPageFigureLifecycleService;
import fmi.ethnowear.domain.model.document.EvidenceState;
import fmi.ethnowear.domain.model.archive.MediaType;
import fmi.ethnowear.domain.model.document.processing.JobStatus;
import fmi.ethnowear.domain.model.document.processing.JobType;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPage;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPageOcrResult;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentProcessingJob;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageMediaRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageOcrResultRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageQualityAssessmentRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentProcessingJobRepository;
import fmi.ethnowear.persistence.jpa.repository.document.KnowledgeChunkPageRepository;
import fmi.ethnowear.util.RowVersionUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class DocumentWorkflowManagementService {

    private static final Set<JobStatus> TERMINAL = Set.of(
            JobStatus.SUCCEEDED,
            JobStatus.FAILED,
            JobStatus.CANCELLED,
            JobStatus.TIMED_OUT,
            JobStatus.DEAD
    );

    private final DocumentPageRepository pageRepository;
    private final DocumentPageMediaRepository pageMediaRepository;
    private final DocumentPageOcrResultRepository ocrResultRepository;
    private final DocumentPageQualityAssessmentRepository assessmentRepository;
    private final DocumentProcessingJobRepository jobRepository;
    private final KnowledgeChunkPageRepository chunkPageRepository;
    private final DocumentProcessingJobScheduler scheduler;
    private final DocumentProcessingJobKeyFactory keyFactory;
    private final DocumentPageQueryMapper pageMapper;
    private final DocumentHistoryMapper historyMapper;
    private final ManagementEventPublisher managementEvents;
    private final DocumentPageFigureLifecycleService figureLifecycleService;
    private final Clock clock;

    @Transactional
    public DocumentPageDetails retirePage(
            Long documentId,
            Long pageId,
            String versionToken,
            String reason,
            String actor
    ) {
        DocumentPage page = requirePage(documentId, pageId);

        RowVersionUtils.requireMatch(page.getRowVersion(), versionToken);

        if (page.getEvidenceState() == EvidenceState.RETIRED)
            return pageDetails(page);

        validateRetirement(reason, actor);

        if (jobRepository.existsByDocumentPage_IdAndActiveJobKeyIsNotNull(pageId))
            throw new DocumentDependencyConflictException(
                    "The page has active processing jobs and cannot be retired"
            );

        if (chunkPageRepository.existsByDocumentPage_Id(pageId))
            throw new DocumentDependencyConflictException(
                    "The page is referenced by knowledge chunks and cannot be retired"
            );

        page.retire(now(), actor, reason);
        figureLifecycleService.markPreviousResultsOutdated(pageId, null);
        page = pageRepository.saveAndFlush(page);
        managementEvents.page(page, ManagementEvent.Action.DELETED);
        return pageDetails(page);
    }

    @Transactional
    public DocumentProcessingJobDetails startPageImageExtraction(
            Long documentId,
            Long pageId
    ) {
        DocumentPage page = requirePage(documentId, pageId);

        if (page.getEvidenceState() == EvidenceState.RETIRED)
            throw new InvalidDocumentProcessingRequestException(
                    "A retired page cannot be processed"
            );

        if (page.getPdfPageIndex() == null)
            throw new InvalidDocumentProcessingRequestException(
                    "PDF page index is required for image extraction"
            );

        if (page.getDocument().getOriginalMediaAsset() == null)
            throw new InvalidDocumentProcessingRequestException(
                    "The document has no original PDF media"
            );

        if (page.getDocument().getOriginalMediaAsset().getMediaType()
                != MediaType.PDF)
            throw new InvalidDocumentProcessingRequestException(
                    "The document original media is not a PDF"
            );

        DocumentProcessingJob job = scheduler.queuePageExtraction(
                page,
                page.getDocument().getOriginalMediaAsset()
        );

        return historyMapper.toDetails(job);
    }

    @Transactional
    public DocumentProcessingJobDetails createReplacementJob(
            Long jobId,
            String versionToken
    ) {
        DocumentProcessingJob previous = requireJob(jobId);
        RowVersionUtils.requireMatch(previous.getRowVersion(), versionToken);

        if (!TERMINAL.contains(previous.getStatus()))
            throw new InvalidDocumentJobTransitionException(
                    "Only terminal OCR or quality jobs can be replaced"
            );

        if (previous.getRetiredAt() != null)
            throw new InvalidDocumentJobTransitionException(
                    "A retired processing job cannot be replaced"
            );

        DocumentProcessingJob replacement = switch (previous.getJobType()) {
            case OCR -> scheduler.createReplacementOcr(previous);
            case OCR_QUALITY_ASSESSMENT ->
                    scheduler.createReplacementQualityAssessment(
                            previous,
                            requireQualityOcrResult(previous)
                    );
            case VISION_OCR_ASSESSMENT ->
                    scheduler.createReplacementVisionOcrAssessment(
                            previous,
                            requireQualityOcrResult(previous),
                            requireVisionDeterministicAssessment(previous)
                    );
            default -> throw new InvalidDocumentJobTransitionException(
                    "Only OCR and assessment jobs can be replaced"
            );
        };

        return historyMapper.toDetails(replacement);
    }

    @Transactional
    public DocumentProcessingJobDetails retireJob(
            Long jobId,
            String versionToken,
            String reason,
            String actor
    ) {
        DocumentProcessingJob job = requireJob(jobId);

        RowVersionUtils.requireMatch(job.getRowVersion(), versionToken);

        if (job.getRetiredAt() != null)
            return historyMapper.toDetails(job);

        validateRetirement(reason, actor);

        if (!TERMINAL.contains(job.getStatus()))
            throw new InvalidDocumentJobTransitionException(
                    "Only terminal processing jobs can be retired"
            );

        if (job.getStatus() == JobStatus.SUCCEEDED)
            throw new InvalidDocumentJobTransitionException(
                    "Successful processing jobs must be preserved"
            );

        ocrResultRepository.findByProcessingJob_Id(jobId)
                .filter(fmi.ethnowear.persistence.jpa.entity.document.DocumentPageOcrResult::isCurrent)
                .ifPresent(result -> {
                    throw new InvalidDocumentJobTransitionException(
                            "The job produced the current OCR result"
                    );
                });

        assessmentRepository.findByProcessingJob_Id(jobId)
                .filter(fmi.ethnowear.persistence.jpa.entity.document.DocumentPageQualityAssessment::isCurrent)
                .ifPresent(assessment -> {
                    throw new InvalidDocumentJobTransitionException(
                            "The job produced the current quality assessment"
                    );
                });

        job.retire(now(), actor, reason);
        job = jobRepository.saveAndFlush(job);
        managementEvents.processingJob(job, ManagementEvent.Action.DELETED);
        return historyMapper.toDetails(job);
    }

    private DocumentPage requirePage(Long documentId, Long pageId) {
        if (documentId == null || pageId == null)
            throw new IllegalArgumentException(
                    "Document and document page identifiers are required"
            );

        return pageRepository.findByIdForUpdate(pageId)
                .filter(page -> page.getDocument() != null)
                .filter(page -> Objects.equals(
                        page.getDocument().getId(),
                        documentId
                ))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Document page",
                        pageId
                ));
    }

    private DocumentProcessingJob requireJob(Long jobId) {
        if (jobId == null)
            throw new IllegalArgumentException("Processing job id is required");

        return jobRepository.findByIdForUpdate(jobId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Document processing job",
                        jobId
                ));
    }

    private DocumentPage requirePageTarget(DocumentProcessingJob job) {
        if (job.getDocumentPage() == null)
            throw new InvalidDocumentJobTransitionException(
                    "The processing job has no page target"
            );

        return job.getDocumentPage();
    }

    private DocumentPageOcrResult requireQualityOcrResult(
            DocumentProcessingJob job
    ) {
        Long resultId;

        try {
            resultId = job.getJobType() == JobType.VISION_OCR_ASSESSMENT
                    ? keyFactory.visionOcrResultId(job.getJobKey())
                    : keyFactory.ocrResultId(job.getJobKey());
        } catch (IllegalArgumentException ex) {
            throw new InvalidDocumentJobTransitionException(ex.getMessage());
        }

        return ocrResultRepository.findById(resultId)
                .filter(result -> result.getDocumentPage() != null)
                .filter(result -> Objects.equals(
                        result.getDocumentPage().getId(),
                        requirePageTarget(job).getId()
                ))
                .orElseThrow(() -> new InvalidDocumentJobTransitionException(
                        "The quality job OCR-result target does not exist"
                ));
    }

    private fmi.ethnowear.persistence.jpa.entity.document.DocumentPageQualityAssessment
    requireVisionDeterministicAssessment(DocumentProcessingJob job) {
        Long assessmentId;

        try {
            assessmentId = keyFactory.visionDeterministicAssessmentId(
                    job.getJobKey()
            );
        } catch (IllegalArgumentException ex) {
            throw new InvalidDocumentJobTransitionException(ex.getMessage());
        }

        return assessmentRepository.findById(assessmentId)
                .filter(assessment -> assessment.getDocumentPage() != null)
                .filter(assessment -> Objects.equals(
                        assessment.getDocumentPage().getId(),
                        requirePageTarget(job).getId()
                ))
                .orElseThrow(() -> new InvalidDocumentJobTransitionException(
                        "The vision job deterministic-assessment target does not exist"
                ));
    }

    private DocumentPageDetails pageDetails(DocumentPage page) {
        return pageMapper.toDetails(
                page,
                pageMediaRepository
                        .findByDocumentPage_IdOrderByDisplayOrderAscIdAsc(
                                page.getId()
                        )
        );
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private void validateRetirement(String reason, String actor) {
        requireAuditText(reason, "Retirement reason", 500);
        requireAuditText(actor, "Retiring user", 150);
    }

    private void requireAuditText(
            String value,
            String field,
            int maximumLength
    ) {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException(field + " is required");

        if (value.trim().length() > maximumLength)
            throw new IllegalArgumentException(
                    field + " cannot exceed " + maximumLength + " characters"
            );
    }
}
