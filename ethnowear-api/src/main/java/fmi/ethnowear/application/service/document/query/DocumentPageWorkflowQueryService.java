package fmi.ethnowear.application.service.document.query;

import fmi.ethnowear.application.dto.document.query.workflow.DocumentPageWorkflowProgressDetails;
import fmi.ethnowear.application.dto.document.query.workflow.DocumentPageWorkflowStepDetails;
import fmi.ethnowear.application.dto.document.query.workflow.DocumentPageWorkflowStepStatus;
import fmi.ethnowear.application.dto.document.query.workflow.DocumentPageWorkflowStepType;
import fmi.ethnowear.application.exception.ResourceNotFoundException;
import fmi.ethnowear.domain.model.document.processing.JobStatus;
import fmi.ethnowear.domain.model.document.processing.JobType;
import fmi.ethnowear.domain.model.document.review.ReviewState;
import fmi.ethnowear.domain.model.document.review.TranscriptionApprovalState;
import fmi.ethnowear.domain.model.document.quality.AssessmentType;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPage;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentProcessingJob;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageMediaRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageOcrResultRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageQualityAssessmentRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentProcessingJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DocumentPageWorkflowQueryService {

    private final DocumentPageRepository pageRepository;
    private final DocumentPageMediaRepository pageMediaRepository;
    private final DocumentPageOcrResultRepository ocrResultRepository;
    private final DocumentPageQualityAssessmentRepository assessmentRepository;
    private final DocumentProcessingJobRepository jobRepository;

    public DocumentPageWorkflowProgressDetails progress(
            Long documentId,
            Long pageId
    ) {
        DocumentPage page = pageRepository
                .findByIdAndDocument_Id(pageId, documentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Document page",
                        pageId
                ));

        Map<JobType, DocumentProcessingJob> latestJobs = latestJobs(pageId);
        boolean hasImage = pageMediaRepository
                .findByDocumentPage_IdAndPreferredOcrInputTrue(pageId)
                .isPresent();
        boolean hasOcr = ocrResultRepository
                .findByDocumentPage_IdAndCurrentTrue(pageId)
                .isPresent();
        var currentAssessments = assessmentRepository
                .findByDocumentPage_IdAndCurrentTrueOrderByAssessmentTypeAscIdAsc(
                        pageId
                );
        boolean hasQuality = currentAssessments.stream().anyMatch(assessment ->
                assessment.getAssessmentType() == AssessmentType.COMBINED_OCR_QUALITY
        );
        boolean hasVision = currentAssessments.stream().anyMatch(assessment ->
                assessment.getAssessmentType() == AssessmentType.VISION_TEXT_COMPARISON
        );

        List<DocumentPageWorkflowStepDetails> steps = new ArrayList<>(4);
        steps.add(jobStep(
                DocumentPageWorkflowStepType.IMAGE_EXTRACTION,
                hasImage,
                latestJobs.get(JobType.PAGE_EXTRACTION),
                null
        ));
        steps.add(jobStep(
                DocumentPageWorkflowStepType.OCR,
                hasOcr,
                latestJobs.get(JobType.OCR),
                hasImage ? null : "Image extraction is required"
        ));
        steps.add(jobStep(
                DocumentPageWorkflowStepType.OCR_QUALITY_ASSESSMENT,
                hasQuality,
                latestJobs.get(JobType.OCR_QUALITY_ASSESSMENT),
                hasOcr ? null : "An OCR result is required"
        ));
        DocumentProcessingJob visionJob = latestJobs.get(
                JobType.VISION_OCR_ASSESSMENT
        );

        if (visionJob != null || hasVision)
            steps.add(jobStep(
                    DocumentPageWorkflowStepType.VISION_OCR_ASSESSMENT,
                    hasVision,
                    visionJob,
                    hasQuality
                            ? null
                            : "A deterministic quality assessment is required"
            ));

        steps.add(reviewStep(page, hasQuality));

        int completed = (int) steps.stream()
                .filter(step -> step.status()
                        == DocumentPageWorkflowStepStatus.COMPLETED)
                .count();

        return new DocumentPageWorkflowProgressDetails(
                documentId,
                pageId,
                completed,
                steps.size(),
                steps
        );
    }

    private Map<JobType, DocumentProcessingJob> latestJobs(Long pageId) {
        Map<JobType, DocumentProcessingJob> latest = new EnumMap<>(JobType.class);

        jobRepository.findByDocumentPage_IdOrderByCreatedAtDescIdDesc(
                pageId,
                PageRequest.of(0, 100)
        ).forEach(job -> {
            if (job.getRetiredAt() == null)
                latest.putIfAbsent(job.getJobType(), job);
        });

        return latest;
    }

    private DocumentPageWorkflowStepDetails jobStep(
            DocumentPageWorkflowStepType step,
            boolean completed,
            DocumentProcessingJob job,
            String blockedMessage
    ) {
        if (completed)
            return new DocumentPageWorkflowStepDetails(
                    step,
                    DocumentPageWorkflowStepStatus.COMPLETED,
                    job == null ? null : job.getId(),
                    job == null ? null : job.getStatus(),
                    null
            );

        if (job == null)
            return new DocumentPageWorkflowStepDetails(
                    step,
                    blockedMessage == null
                            ? DocumentPageWorkflowStepStatus.NOT_STARTED
                            : DocumentPageWorkflowStepStatus.BLOCKED,
                    null,
                    null,
                    blockedMessage
            );

        return new DocumentPageWorkflowStepDetails(
                step,
                workflowStatus(job.getStatus()),
                job.getId(),
                job.getStatus(),
                job.getSafeErrorMessage()
        );
    }

    private DocumentPageWorkflowStepDetails reviewStep(
            DocumentPage page,
            boolean hasQuality
    ) {
        DocumentPageWorkflowStepStatus status;

        if (page.getTranscriptionApprovalState()
                == TranscriptionApprovalState.APPROVED)
            status = DocumentPageWorkflowStepStatus.COMPLETED;
        else if (page.getTranscriptionApprovalState()
                == TranscriptionApprovalState.REJECTED
                || page.getReviewState() == ReviewState.REJECTED)
            status = DocumentPageWorkflowStepStatus.FAILED;
        else if (page.getReviewState() == ReviewState.IN_REVIEW)
            status = DocumentPageWorkflowStepStatus.IN_PROGRESS;
        else if (page.getReviewState() == ReviewState.REVIEW_REQUIRED)
            status = DocumentPageWorkflowStepStatus.QUEUED;
        else
            status = hasQuality
                    ? DocumentPageWorkflowStepStatus.NOT_STARTED
                    : DocumentPageWorkflowStepStatus.BLOCKED;

        return new DocumentPageWorkflowStepDetails(
                DocumentPageWorkflowStepType.HUMAN_REVIEW,
                status,
                null,
                null,
                hasQuality ? null : "Quality assessment is required"
        );
    }

    private DocumentPageWorkflowStepStatus workflowStatus(JobStatus status) {
        return switch (status) {
            case QUEUED, RETRY_WAIT -> DocumentPageWorkflowStepStatus.QUEUED;
            case CLAIMED, RUNNING, CANCEL_REQUESTED ->
                    DocumentPageWorkflowStepStatus.IN_PROGRESS;
            case SUCCEEDED -> DocumentPageWorkflowStepStatus.COMPLETED;
            case FAILED, TIMED_OUT, DEAD ->
                    DocumentPageWorkflowStepStatus.FAILED;
            case CANCELLED -> DocumentPageWorkflowStepStatus.CANCELLED;
        };
    }
}
