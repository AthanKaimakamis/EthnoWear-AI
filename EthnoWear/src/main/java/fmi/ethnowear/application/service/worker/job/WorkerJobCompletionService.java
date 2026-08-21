package fmi.ethnowear.application.service.worker.job;

import fmi.ethnowear.application.dto.worker.completion.WorkerJobCompletionDetails;
import fmi.ethnowear.application.exception.WorkerClaimConflictException;
import fmi.ethnowear.application.exception.WorkerManifestConflictException;
import fmi.ethnowear.application.model.worker.WorkerClaimCredentials;
import fmi.ethnowear.application.service.document.processing.*;
import fmi.ethnowear.application.service.worker.security.WorkerClaimedJobLoader;
import fmi.ethnowear.domain.model.document.DocumentPageRenditionType;
import fmi.ethnowear.domain.model.document.processing.*;
import fmi.ethnowear.persistence.jpa.entity.MediaAsset;
import fmi.ethnowear.persistence.jpa.entity.document.*;
import fmi.ethnowear.persistence.jpa.repository.document.*;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WorkerJobCompletionService {

    private final WorkerClaimedJobLoader jobLoader;
    private final DocumentProcessingJobRepository jobRepository;
    private final DocumentPageRepository pageRepository;
    private final DocumentPageMediaRepository pageMediaRepository;
    private final DocumentRepository documentRepository;
    private final DocumentProcessingJobScheduler jobScheduler;
    private final DocumentProcessingJobKeyFactory jobKeyFactory;
    private final Clock clock;

    @Transactional
    public WorkerJobCompletionDetails complete(Long jobId, WorkerClaimCredentials credentials) {
        DocumentProcessingJob job = jobLoader.loadForUpdate(jobId);

        if (job.getJobType() != JobType.PAGE_EXTRACTION || job.getDocument() == null)
            throw new WorkerManifestConflictException("Job is not a document page-extraction job");

        Document document = job.getDocument();

        if (job.getStatus() == JobStatus.SUCCEEDED)
            return existingResult(job, document);

        jobLoader.validateActive(job, credentials);

        if (job.getStatus() == JobStatus.CANCEL_REQUESTED)
            throw new WorkerClaimConflictException();

        List<DocumentPage> pages = pageRepository
                .findByDocument_IdOrderByPageSequenceAsc(document.getId());

        if (pages.isEmpty())
            throw new WorkerManifestConflictException("Document has no extracted pages");

        List<Long> pageIds = pages.stream()
                .map(DocumentPage::getId)
                .toList();

        Set<Long> renderedPageIds = new HashSet<>(
                pageMediaRepository.findPageIdsWithRendition(
                        pageIds,
                        DocumentPageRenditionType.PDF_PAGE_RENDER
                )
        );

        if (renderedPageIds.size() != pages.size())
            throw new WorkerManifestConflictException("Not every document page has a rendered rendition");

        Map<Long, DocumentPageMedia> preferredInputs =
                pageMediaRepository
                        .findByDocumentPage_IdInAndPreferredOcrInputTrue(pageIds)
                        .stream()
                        .collect(Collectors.toMap(
                                media -> media.getDocumentPage().getId(),
                                Function.identity()
                        ));

        if (preferredInputs.size() != pages.size())
            throw new WorkerManifestConflictException("Not every document page has a preferred OCR input");

        int queuedOcrJobs = 0;

        for (DocumentPage page : pages) {
            page.setProcessingState(ProcessingState.PENDING);

            MediaAsset input = preferredInputs
                    .get(page.getId())
                    .getMediaAsset();

            if (queueOcrIfMissing(page, input))
                queuedOcrJobs++;
        }

        LocalDateTime now = LocalDateTime.ofInstant(
                clock.instant(),
                ZoneOffset.UTC
        );

        document.setPageCount(pages.size());
        document.setProcessingState(ProcessingState.PROCESSING);

        job.setStatus(JobStatus.SUCCEEDED);
        job.setFinishedAt(now);
        job.setErrorCode(null);
        job.setSafeErrorMessage(null);
        job.setErrorDetailsJson(null);
        job.setCancellationReason(null);
        job.clearClaimOwnership();
        job.clearActiveJobKey();

        pageRepository.saveAll(pages);
        documentRepository.save(document);
        jobRepository.saveAndFlush(job);

        return new WorkerJobCompletionDetails(
                job.getId(),
                document.getId(),
                pages.size(),
                queuedOcrJobs,
                false
        );
    }

    private boolean queueOcrIfMissing(
            @NonNull DocumentPage page,
            MediaAsset input
    ) {
        String activeJobKey = jobKeyFactory.forPage(JobType.OCR, page.getId());

        if (jobRepository.findByActiveJobKey(activeJobKey).isPresent())
            return false;

        jobScheduler.queueOcr(page, input);
        return true;
    }

    private @NonNull WorkerJobCompletionDetails existingResult(
            @NonNull DocumentProcessingJob job,
            @NonNull Document document) {
        int pageCount = Math.toIntExact(pageRepository.countByDocument_Id(document.getId()));

        return new WorkerJobCompletionDetails(
                job.getId(),
                document.getId(),
                pageCount,
                0,
                true
        );
    }
}
