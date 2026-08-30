package fmi.ethnowear.application.service.document.figure;

import fmi.ethnowear.application.service.document.processing.DocumentProcessingJobKeyFactory;
import fmi.ethnowear.domain.model.document.figure.FigureExtractionState;
import fmi.ethnowear.domain.model.document.processing.JobStatus;
import fmi.ethnowear.domain.model.document.processing.JobType;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPageOcrResult;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentProcessingJob;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageOcrResultRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentProcessingJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class FigureExtractionJobStateService {

    private static final Set<JobStatus> RECOVERED_STATUSES = Set.of(
            JobStatus.RETRY_WAIT,
            JobStatus.CANCELLED,
            JobStatus.DEAD
    );

    private final DocumentProcessingJobRepository jobRepository;
    private final DocumentPageOcrResultRepository ocrResultRepository;
    private final DocumentProcessingJobKeyFactory keyFactory;

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordFailure(
            DocumentProcessingJob job,
            boolean terminal,
            String safeMessage
    ) {
        if (job.getJobType() != JobType.EXTRACT_PAGE_FIGURES)
            return;

        update(job, terminal ? FigureExtractionState.FAILED : FigureExtractionState.PENDING,
                terminal ? safeMessage : null);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordCancellation(DocumentProcessingJob job) {
        if (job.getJobType() == JobType.EXTRACT_PAGE_FIGURES)
            update(job, FigureExtractionState.FAILED, "Figure extraction was cancelled.");
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void reconcileRecovered(LocalDateTime recoveryTime) {
        jobRepository.findByJobTypeAndUpdatedAtAndStatusIn(
                JobType.EXTRACT_PAGE_FIGURES,
                recoveryTime,
                RECOVERED_STATUSES
        ).forEach(job -> {
            if (job.getStatus() == JobStatus.RETRY_WAIT)
                update(job, FigureExtractionState.PENDING, null);
            else
                update(job, FigureExtractionState.FAILED, job.getSafeErrorMessage());
        });
    }

    private void update(
            DocumentProcessingJob job,
            FigureExtractionState state,
            String message
    ) {
        Long ocrResultId = keyFactory.figureOcrResultId(job.getJobKey());
        DocumentPageOcrResult result = ocrResultRepository.findById(ocrResultId)
                .orElse(null);

        if (result == null)
            return;

        if (!result.isCurrent()) {
            result.setFigureExtractionState(FigureExtractionState.OUTDATED);
            result.setFigureExtractionMessage(null);
            return;
        }

        result.setFigureExtractionState(state);
        result.setFigureExtractionMessage(normalize(message));
    }

    private String normalize(String message) {
        if (message == null || message.isBlank())
            return null;

        String trimmed = message.trim();
        return trimmed.length() <= 500 ? trimmed : trimmed.substring(0, 500);
    }
}
