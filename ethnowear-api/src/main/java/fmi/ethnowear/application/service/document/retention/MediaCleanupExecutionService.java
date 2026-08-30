package fmi.ethnowear.application.service.document.retention;

import fmi.ethnowear.application.dto.document.query.retention.MediaCleanupEligibilityDetails;
import fmi.ethnowear.application.exception.DocumentDependencyConflictException;
import fmi.ethnowear.application.exception.ResourceNotFoundException;
import fmi.ethnowear.domain.model.document.processing.JobStatus;
import fmi.ethnowear.domain.model.document.processing.JobType;
import fmi.ethnowear.domain.model.media.MediaOrigin;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentProcessingJob;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageMediaRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentProcessingJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MediaCleanupExecutionService {

    private final DocumentProcessingJobRepository jobRepository;
    private final DocumentPageMediaRepository pageMediaRepository;
    private final MediaCleanupEligibilityService eligibilityService;
    private final GeneratedDocumentMediaPolicy generatedMediaPolicy;
    private final MediaAssetPurgeService purgeService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public int execute(Long jobId) {
        DocumentProcessingJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Document processing job",
                        jobId
                ));

        if (job.getJobType() != JobType.MEDIA_CLEANUP
                || job.getStatus() != JobStatus.RUNNING
                || job.getDocument() == null)
            throw new IllegalStateException(
                    "Processing job is not an active media-cleanup job"
            );

        MediaCleanupEligibilityDetails eligibility =
                eligibilityService.evaluate(job.getDocument(), false);

        if (!eligibility.eligible())
            throw new DocumentDependencyConflictException(
                    String.join("; ", eligibility.blockers())
            );

        List<Long> candidateIds = pageMediaRepository.findCleanupCandidates(
                        job.getDocument().getId(),
                        generatedMediaPolicy.renditionTypes(),
                        MediaOrigin.GENERATED,
                        now()
                ).stream()
                .map(media -> media.getMediaAsset().getId())
                .distinct()
                .toList();

        int purged = 0;
        for (Long candidateId : candidateIds) {
            if (purgeService.purge(candidateId))
                purged++;
        }

        return purged;
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
