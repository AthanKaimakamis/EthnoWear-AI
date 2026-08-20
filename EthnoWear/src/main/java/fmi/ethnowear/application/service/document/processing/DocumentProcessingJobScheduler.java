package fmi.ethnowear.application.service.document.processing;

import fmi.ethnowear.application.exception.ActiveDocumentJobExistsException;
import fmi.ethnowear.domain.model.document.processing.JobStatus;
import fmi.ethnowear.domain.model.document.processing.JobType;
import fmi.ethnowear.persistence.jpa.entity.MediaAsset;
import fmi.ethnowear.persistence.jpa.entity.document.Document;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPage;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentProcessingJob;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentProcessingJobRepository;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DocumentProcessingJobScheduler {

    private static final String ACTIVE_JOB_CONSTRAINT = "UQ_DocumentProcessingJobs_ActiveJobKey";

    private final DocumentProcessingJobRepository repository;
    private final DocumentProcessingJobKeyFactory keyFactory;

    @Transactional(propagation = Propagation.MANDATORY)
    public DocumentProcessingJob queuePageExtraction(Document document, MediaAsset originalMedia) {
        Objects.requireNonNull(document, "Document is required");
        Objects.requireNonNull(originalMedia, "Original media is required");

        return queue(
                JobType.PAGE_EXTRACTION,
                document,
                null,
                originalMedia,
                keyFactory.forDocument(
                        JobType.PAGE_EXTRACTION,
                        document.getId()
                )
        );
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public DocumentProcessingJob queueOcr(DocumentPage page, MediaAsset inputMedia) {
        Objects.requireNonNull(page, "Document page is required");
        Objects.requireNonNull(inputMedia, "OCR input media is required");

        return queue(
                JobType.OCR,
                page.getDocument(),
                page,
                inputMedia,
                keyFactory.forPage(JobType.OCR, page.getId())
        );
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public DocumentProcessingJob queueOcrQualityAssessment(
            DocumentPage page,
            MediaAsset inputMedia
    ) {
        Objects.requireNonNull(page, "Document page is required");
        Objects.requireNonNull(inputMedia, "Assessment input media is required");

        return queue(
                JobType.OCR_QUALITY_ASSESSMENT,
                page.getDocument(),
                page,
                inputMedia,
                keyFactory.forPage(
                        JobType.OCR_QUALITY_ASSESSMENT,
                        page.getId()
                )
        );
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public DocumentProcessingJob queueChunkGeneration(Document document) {
        Objects.requireNonNull(document, "Document is required");

        return queue(
                JobType.CHUNK_GENERATION,
                document,
                null,
                null,
                keyFactory.forDocument(
                        JobType.CHUNK_GENERATION,
                        document.getId()
                )
        );
    }

    private @NonNull DocumentProcessingJob queue(
            JobType jobType,
            Document document,
            DocumentPage page,
            MediaAsset inputMedia,
            String activeJobKey
    ) {
        if (repository.findByActiveJobKey(activeJobKey).isPresent())
            throw new ActiveDocumentJobExistsException(jobType);

        DocumentProcessingJob job = new DocumentProcessingJob();
        job.setJobType(jobType);
        job.setStatus(JobStatus.QUEUED);
        job.setDocument(document);
        job.setDocumentPage(page);
        job.setInputMediaAsset(inputMedia);
        job.setPriority(0);
        job.setAttemptCount(0);
        job.setMaxAttempts(3);
        job.setAvailableAt(LocalDateTime.now(ZoneOffset.UTC));
        job.setCorrelationId(UUID.randomUUID());
        job.assignActiveJobKey(activeJobKey);

        try {
            return repository.saveAndFlush(job);
        } catch (DataIntegrityViolationException ex) {
            if (isActiveJobConflict(ex))
                throw new ActiveDocumentJobExistsException(jobType);

            throw ex;
        }
    }

    private boolean isActiveJobConflict(Throwable error) {
        Throwable current = error;

        while (current != null) {
            if (current instanceof SQLException sqlException
                    && (sqlException.getErrorCode() == 2601 || sqlException.getErrorCode() == 2627)
                    && containsConstraint(sqlException.getMessage()))
                return true;

            if (containsConstraint(current.getMessage()))
                return true;

            current = current.getCause();
        }

        return false;
    }

    private boolean containsConstraint(String message) {
        return message != null && message.contains(ACTIVE_JOB_CONSTRAINT);
    }
}
