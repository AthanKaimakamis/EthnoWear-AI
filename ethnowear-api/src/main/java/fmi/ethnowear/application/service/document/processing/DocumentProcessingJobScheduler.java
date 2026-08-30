package fmi.ethnowear.application.service.document.processing;

import fmi.ethnowear.application.exception.ActiveDocumentJobExistsException;
import fmi.ethnowear.application.exception.InvalidDocumentProcessingRequestException;
import fmi.ethnowear.application.exception.VisionJobAlreadyActiveException;
import fmi.ethnowear.application.model.event.ManagementEvent;
import fmi.ethnowear.application.service.event.ManagementEventPublisher;
import fmi.ethnowear.domain.model.document.processing.JobStatus;
import fmi.ethnowear.domain.model.document.processing.JobType;
import fmi.ethnowear.persistence.jpa.entity.MediaAsset;
import fmi.ethnowear.persistence.jpa.entity.KnowledgeChunk;
import fmi.ethnowear.persistence.jpa.entity.document.Document;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPage;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPageOcrResult;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPageQualityAssessment;
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
import fmi.ethnowear.domain.model.media.MediaRetentionPolicy;

@Service
@RequiredArgsConstructor
public class DocumentProcessingJobScheduler {

    private static final String ACTIVE_JOB_CONSTRAINT = "UQ_DocumentProcessingJobs_ActiveJobKey";

    private final DocumentProcessingJobRepository repository;
    private final DocumentProcessingJobKeyFactory keyFactory;
    private final ManagementEventPublisher managementEvents;

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
    public DocumentProcessingJob queuePageExtraction(
            DocumentPage page,
            MediaAsset originalMedia
    ) {
        Objects.requireNonNull(page, "Document page is required");
        Objects.requireNonNull(originalMedia, "Original document media is required");

        String activeJobKey = keyFactory.forPage(
                JobType.PAGE_EXTRACTION,
                page.getId()
        );

        repository.findByActiveJobKey(activeJobKey).ifPresent(active -> {
            throw new ActiveDocumentJobExistsException(JobType.PAGE_EXTRACTION);
        });

        return create(
                JobType.PAGE_EXTRACTION,
                page.getDocument(),
                page,
                originalMedia,
                keyFactory.forRecreatedPageJob(
                        JobType.PAGE_EXTRACTION,
                        page.getId(),
                        UUID.randomUUID()
                ),
                activeJobKey,
                latestPageJob(page, JobType.PAGE_EXTRACTION)
        );
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public DocumentProcessingJob queueOcr(DocumentPage page, MediaAsset inputMedia) {
        Objects.requireNonNull(page, "Document page is required");
        Objects.requireNonNull(inputMedia, "OCR input media is required");

        String activeJobKey = keyFactory.forPage(JobType.OCR, page.getId());

        repository.findByActiveJobKey(activeJobKey).ifPresent(active -> {
            throw new ActiveDocumentJobExistsException(JobType.OCR);
        });

        DocumentProcessingJob previousJob = latestPageJob(page, JobType.OCR);

        return create(
                JobType.OCR,
                page.getDocument(),
                page,
                inputMedia,
                keyFactory.forRecreatedPageJob(
                        JobType.OCR,
                        page.getId(),
                        UUID.randomUUID()
                ),
                activeJobKey,
                previousJob
        );
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public DocumentProcessingJob createNewOcrJob(
            DocumentPage page,
            MediaAsset inputMedia
    ) {
        return queueOcr(page, inputMedia);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public DocumentProcessingJob createReplacementOcr(
            DocumentProcessingJob previousJob
    ) {
        Objects.requireNonNull(previousJob, "Previous processing job is required");

        DocumentPage page = previousJob.getDocumentPage();
        MediaAsset inputMedia = previousJob.getInputMediaAsset();

        if (previousJob.getJobType() != JobType.OCR
                || page == null
                || inputMedia == null)
            throw new IllegalArgumentException(
                    "OCR job has no replacement target"
            );

        String activeJobKey = keyFactory.forPage(JobType.OCR, page.getId());

        repository.findByActiveJobKey(activeJobKey).ifPresent(active -> {
            throw new ActiveDocumentJobExistsException(JobType.OCR);
        });

        return create(
                JobType.OCR,
                page.getDocument(),
                page,
                inputMedia,
                keyFactory.forRecreatedPageJob(
                        JobType.OCR,
                        page.getId(),
                        UUID.randomUUID()
                ),
                activeJobKey,
                previousJob
        );
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public DocumentProcessingJob queueOcrQualityAssessment(
            DocumentPage page,
            MediaAsset inputMedia,
            DocumentPageOcrResult ocrResult
    ) {
        Objects.requireNonNull(page, "Document page is required");
        Objects.requireNonNull(inputMedia, "Assessment input media is required");
        Objects.requireNonNull(ocrResult, "OCR result is required");

        String jobKey = keyFactory.forOcrResult(ocrResult.getId());

        return queue(
                JobType.OCR_QUALITY_ASSESSMENT,
                page.getDocument(),
                page,
                inputMedia,
                jobKey
        );
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public DocumentProcessingJob queueFigureExtraction(
            DocumentPage page,
            MediaAsset inputMedia,
            DocumentPageOcrResult ocrResult
    ) {
        Objects.requireNonNull(page, "Document page is required");
        Objects.requireNonNull(inputMedia, "Figure extraction input media is required");
        Objects.requireNonNull(ocrResult, "OCR result is required");

        if (!ocrResult.isCurrent()
                || ocrResult.getDocumentPage() == null
                || !Objects.equals(ocrResult.getDocumentPage().getId(), page.getId()))
            throw new InvalidDocumentProcessingRequestException(
                    "The referenced OCR result is no longer current"
            );

        String activeJobKey = keyFactory.forFigureExtraction(
                page.getId(),
                ocrResult.getId()
        );

        repository.findByActiveJobKey(activeJobKey).ifPresent(active -> {
            throw new ActiveDocumentJobExistsException(JobType.EXTRACT_PAGE_FIGURES);
        });

        return create(
                JobType.EXTRACT_PAGE_FIGURES,
                page.getDocument(),
                page,
                inputMedia,
                activeJobKey + ":JOB:" + UUID.randomUUID(),
                activeJobKey,
                latestPageJob(page, JobType.EXTRACT_PAGE_FIGURES)
        );
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public DocumentProcessingJob createReplacementQualityAssessment(
            DocumentProcessingJob previousJob,
            DocumentPageOcrResult ocrResult
    ) {
        Objects.requireNonNull(previousJob, "Previous processing job is required");
        Objects.requireNonNull(ocrResult, "OCR result is required");

        DocumentPage page = previousJob.getDocumentPage();
        MediaAsset inputMedia = previousJob.getInputMediaAsset();

        if (page == null || inputMedia == null)
            throw new IllegalArgumentException(
                    "Quality-assessment job has no replacement target"
            );

        String activeJobKey = keyFactory.forOcrResult(ocrResult.getId());

        repository.findByActiveJobKey(activeJobKey).ifPresent(active -> {
            throw new ActiveDocumentJobExistsException(
                    JobType.OCR_QUALITY_ASSESSMENT
            );
        });

        return create(
                JobType.OCR_QUALITY_ASSESSMENT,
                page.getDocument(),
                page,
                inputMedia,
                activeJobKey + ":JOB:" + UUID.randomUUID(),
                activeJobKey,
                previousJob
        );
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public DocumentProcessingJob queueVisionOcrAssessment(
            DocumentPage page,
            MediaAsset inputMedia,
            DocumentPageOcrResult ocrResult,
            DocumentPageQualityAssessment deterministicAssessment
    ) {
        Objects.requireNonNull(page, "Document page is required");
        Objects.requireNonNull(inputMedia, "Vision-assessment input media is required");
        Objects.requireNonNull(ocrResult, "OCR result is required");
        Objects.requireNonNull(
                deterministicAssessment,
                "Deterministic assessment is required"
        );

        validateCurrentVisionEvidence(page, inputMedia, ocrResult, deterministicAssessment);

        String activeJobKey = keyFactory.forActiveVisionReview(
                page.getId(),
                ocrResult.getId()
        );
        DocumentProcessingJob previousJob = latestPageJob(
                page,
                JobType.VISION_OCR_ASSESSMENT
        );

        repository.findByActiveJobKey(activeJobKey).ifPresent(active -> {
            throw visionConflict(active, ocrResult.getId());
        });

        String jobKey = keyFactory.forVisionEvidence(
                ocrResult.getId(),
                deterministicAssessment.getId()
        ) + ":JOB:" + UUID.randomUUID();

        return create(
                JobType.VISION_OCR_ASSESSMENT,
                page.getDocument(),
                page,
                inputMedia,
                jobKey,
                activeJobKey,
                previousJob
        );
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public DocumentProcessingJob createReplacementVisionOcrAssessment(
            DocumentProcessingJob previousJob,
            DocumentPageOcrResult ocrResult,
            DocumentPageQualityAssessment deterministicAssessment
    ) {
        Objects.requireNonNull(previousJob, "Previous processing job is required");
        Objects.requireNonNull(ocrResult, "OCR result is required");
        Objects.requireNonNull(
                deterministicAssessment,
                "Deterministic assessment is required"
        );

        DocumentPage page = previousJob.getDocumentPage();
        MediaAsset inputMedia = previousJob.getInputMediaAsset();

        if (page == null || inputMedia == null)
            throw new IllegalArgumentException(
                    "Vision-assessment job has no replacement target"
            );

        validateCurrentVisionEvidence(
                page,
                inputMedia,
                ocrResult,
                deterministicAssessment
        );

        String activeJobKey = keyFactory.forActiveVisionReview(
                page.getId(),
                ocrResult.getId()
        );
        DocumentProcessingJob latestPreviousJob = latestPageJob(
                page,
                JobType.VISION_OCR_ASSESSMENT
        );

        repository.findByActiveJobKey(activeJobKey).ifPresent(active -> {
            throw visionConflict(active, ocrResult.getId());
        });

        return create(
                JobType.VISION_OCR_ASSESSMENT,
                page.getDocument(),
                page,
                inputMedia,
                keyFactory.forVisionEvidence(
                        ocrResult.getId(),
                        deterministicAssessment.getId()
                ) + ":JOB:" + UUID.randomUUID(),
                activeJobKey,
                latestPreviousJob
        );
    }

    private void validateCurrentVisionEvidence(
            DocumentPage page,
            MediaAsset inputMedia,
            DocumentPageOcrResult ocrResult,
            DocumentPageQualityAssessment deterministicAssessment
    ) {
        if (!ocrResult.isCurrent()
                || ocrResult.getDocumentPage() == null
                || !page.getId().equals(ocrResult.getDocumentPage().getId()))
            throw new InvalidDocumentProcessingRequestException(
                    "The referenced OCR result is no longer current"
            );

        if (ocrResult.getDocumentPageMedia() == null
                || ocrResult.getDocumentPageMedia().getMediaAsset() == null
                || !inputMedia.getId().equals(
                ocrResult.getDocumentPageMedia().getMediaAsset().getId()
        ))
            throw new InvalidDocumentProcessingRequestException(
                    "The referenced OCR rendition is no longer current"
            );

        if (!deterministicAssessment.isCurrent()
                || deterministicAssessment.getDocumentPage() == null
                || !page.getId().equals(
                deterministicAssessment.getDocumentPage().getId()
        )
                || deterministicAssessment.getDocumentPageOcrResult() == null
                || !ocrResult.getId().equals(
                deterministicAssessment.getDocumentPageOcrResult().getId()
        ))
            throw new InvalidDocumentProcessingRequestException(
                    "The deterministic assessment is no longer current"
            );
    }

    private VisionJobAlreadyActiveException visionConflict(
            DocumentProcessingJob active,
            Long ocrResultId
    ) {
        return new VisionJobAlreadyActiveException(
                active.getId(),
                active.getStatus(),
                active.getDocumentPage() == null
                        ? null
                        : active.getDocumentPage().getId(),
                ocrResultId
        );
    }

    private DocumentProcessingJob latestPageJob(
            DocumentPage page,
            JobType jobType
    ) {
        return repository
                .findFirstByDocumentPage_IdAndJobTypeOrderByCreatedAtDescIdDesc(
                        page.getId(),
                        jobType
                )
                .orElse(null);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public DocumentProcessingJob queueChunkGeneration(
            Document document,
            String generationInputHash
    ) {
        Objects.requireNonNull(document, "Document is required");
        if (generationInputHash == null || generationInputHash.isBlank())
            throw new IllegalArgumentException(
                    "Chunk-generation input hash is required"
            );

        String activeJobKey = keyFactory.forDocument(
                JobType.CHUNK_GENERATION,
                document.getId()
        );
        String jobKey = activeJobKey + ":INPUT:" + generationInputHash;

        repository.findByActiveJobKey(activeJobKey).ifPresent(active -> {
            throw new ActiveDocumentJobExistsException(
                    JobType.CHUNK_GENERATION
            );
        });

        DocumentProcessingJob existing = repository
                .findByJobKeyForUpdate(jobKey)
                .orElse(null);

        if (existing != null)
            return queue(
                    JobType.CHUNK_GENERATION,
                    document,
                    null,
                    null,
                    activeJobKey,
                    existing
            );

        DocumentProcessingJob previous = repository
                .findFirstByDocument_IdAndJobTypeOrderByCreatedAtDescIdDesc(
                        document.getId(),
                        JobType.CHUNK_GENERATION
                )
                .orElse(null);

        return create(
                JobType.CHUNK_GENERATION,
                document,
                null,
                null,
                jobKey,
                activeJobKey,
                previous,
                LocalDateTime.now(ZoneOffset.UTC),
                "{\"generationInputHash\":\""
                        + generationInputHash
                        + "\"}"
        );
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public DocumentProcessingJob queueIndexChunk(KnowledgeChunk chunk) {
        Objects.requireNonNull(chunk, "Knowledge chunk is required");

        return queue(
                JobType.INDEX_CHUNK,
                chunk.getDocument(),
                null,
                null,
                keyFactory.forChunk(JobType.INDEX_CHUNK, chunk.getId()),
                null,
                chunk
        );
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public DocumentProcessingJob queueMediaCleanup(
            Document document,
            LocalDateTime retentionUntil,
            MediaRetentionPolicy retentionPolicy
    ) {
        Objects.requireNonNull(document, "Document is required");
        Objects.requireNonNull(retentionUntil, "Retention deadline is required");

        if (retentionPolicy != MediaRetentionPolicy.KEEP_ORIGINAL_ONLY)
            throw new IllegalArgumentException(
                    "Only KEEP_ORIGINAL_ONLY cleanup is supported"
            );

        String activeJobKey = keyFactory.forDocument(
                JobType.MEDIA_CLEANUP,
                document.getId()
        );

        repository.findByActiveJobKey(activeJobKey).ifPresent(active -> {
            throw new ActiveDocumentJobExistsException(JobType.MEDIA_CLEANUP);
        });

        DocumentProcessingJob previous = repository
                .findFirstByDocument_IdAndJobTypeOrderByCreatedAtDescIdDesc(
                        document.getId(),
                        JobType.MEDIA_CLEANUP
                )
                .orElse(null);

        return create(
                JobType.MEDIA_CLEANUP,
                document,
                null,
                null,
                activeJobKey + ":JOB:" + UUID.randomUUID(),
                activeJobKey,
                previous,
                retentionUntil,
                "{\"policy\":\"KEEP_ORIGINAL_ONLY\"}"
        );
    }

    private @NonNull DocumentProcessingJob queue(
            JobType jobType,
            Document document,
            DocumentPage page,
            MediaAsset inputMedia,
            String activeJobKey
    ) {
        DocumentProcessingJob existing = repository
                .findByJobKeyForUpdate(activeJobKey)
                .orElse(null);

        return queue(
                jobType,
                document,
                page,
                inputMedia,
                activeJobKey,
                existing
        );
    }

    private @NonNull DocumentProcessingJob queue(
            JobType jobType,
            Document document,
            DocumentPage page,
            MediaAsset inputMedia,
            String activeJobKey,
            DocumentProcessingJob existing
    ) {

        if (existing != null) {
            if (existing.getActiveJobKey() != null)
                throw new ActiveDocumentJobExistsException(jobType);

            if (existing.getStatus() == JobStatus.SUCCEEDED)
                return existing;

            existing.setStatus(JobStatus.QUEUED);
            existing.setDocument(document);
            existing.setDocumentPage(page);
            existing.setInputMediaAsset(inputMedia);
            existing.setAttemptCount(0);
            existing.setAvailableAt(LocalDateTime.now(ZoneOffset.UTC));
            existing.setStartedAt(null);
            existing.setFinishedAt(null);
            existing.setErrorCode(null);
            existing.setSafeErrorMessage(null);
            existing.setErrorDetailsJson(null);
            existing.setCancellationReason(null);
            existing.clearClaimOwnership();
            if (existing.getJobKey() == null)
                existing.assignJobKey(activeJobKey);
            existing.assignActiveJobKey(activeJobKey);

            DocumentProcessingJob savedJob = repository.saveAndFlush(existing);
            managementEvents.processingJob(
                    savedJob,
                    ManagementEvent.Action.STATUS_CHANGED
            );
            return savedJob;
        }

        return create(
                jobType,
                document,
                page,
                inputMedia,
                activeJobKey,
                activeJobKey,
                null
        );
    }

    private DocumentProcessingJob create(
            JobType jobType,
            Document document,
            DocumentPage page,
            MediaAsset inputMedia,
            String jobKey,
            String activeJobKey,
            DocumentProcessingJob previousJob
    ) {
        return create(
                jobType,
                document,
                page,
                inputMedia,
                jobKey,
                activeJobKey,
                previousJob,
                LocalDateTime.now(ZoneOffset.UTC),
                null
        );
    }

    private DocumentProcessingJob queue(
            JobType jobType,
            Document document,
            DocumentPage page,
            MediaAsset inputMedia,
            String activeJobKey,
            DocumentProcessingJob previousJob,
            KnowledgeChunk chunk
    ) {
        DocumentProcessingJob existing = repository
                .findByJobKeyForUpdate(activeJobKey)
                .orElse(null);

        if (existing != null)
            return queue(
                    jobType,
                    document,
                    page,
                    inputMedia,
                    activeJobKey,
                    existing
            );

        return create(
                jobType,
                document,
                page,
                inputMedia,
                activeJobKey,
                activeJobKey,
                previousJob,
                LocalDateTime.now(ZoneOffset.UTC),
                null,
                chunk
        );
    }

    private DocumentProcessingJob create(
            JobType jobType,
            Document document,
            DocumentPage page,
            MediaAsset inputMedia,
            String jobKey,
            String activeJobKey,
            DocumentProcessingJob previousJob,
            LocalDateTime availableAt,
            String parametersJson
    ) {
        return create(
                jobType,
                document,
                page,
                inputMedia,
                jobKey,
                activeJobKey,
                previousJob,
                availableAt,
                parametersJson,
                null
        );
    }

    private DocumentProcessingJob create(
            JobType jobType,
            Document document,
            DocumentPage page,
            MediaAsset inputMedia,
            String jobKey,
            String activeJobKey,
            DocumentProcessingJob previousJob,
            LocalDateTime availableAt,
            String parametersJson,
            KnowledgeChunk chunk
    ) {
        DocumentProcessingJob job = new DocumentProcessingJob();
        job.setJobType(jobType);
        job.setStatus(JobStatus.QUEUED);
        job.setDocument(document);
        job.setDocumentPage(page);
        job.setInputMediaAsset(inputMedia);
        job.setKnowledgeChunk(chunk);
        job.setPriority(0);
        job.setAttemptCount(0);
        job.setMaxAttempts(3);
        job.setAvailableAt(availableAt);
        job.setParametersJson(parametersJson);
        job.setCorrelationId(UUID.randomUUID());
        job.assignJobKey(jobKey);
        job.assignActiveJobKey(activeJobKey);
        job.linkPreviousJob(previousJob);

        try {
            DocumentProcessingJob savedJob = repository.saveAndFlush(job);
            managementEvents.processingJob(
                    savedJob,
                    ManagementEvent.Action.CREATED
            );
            return savedJob;
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
