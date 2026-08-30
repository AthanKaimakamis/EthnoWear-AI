package fmi.ethnowear.application.service.worker.job;

import fmi.ethnowear.application.exception.ResourceNotFoundException;
import fmi.ethnowear.application.exception.UnprocessableDocumentEvidenceException;
import fmi.ethnowear.application.exception.WorkerClaimConflictException;
import fmi.ethnowear.application.model.worker.WorkerClaimCredentials;
import fmi.ethnowear.application.service.archive.media.delivery.MediaDelivery;
import fmi.ethnowear.application.service.archive.media.delivery.MediaDeliveryService;
import fmi.ethnowear.application.service.worker.security.WorkerClaimedJobLoader;
import fmi.ethnowear.config.WorkerApiProperties;
import fmi.ethnowear.domain.model.archive.MediaType;
import fmi.ethnowear.domain.model.document.processing.JobStatus;
import fmi.ethnowear.domain.model.document.processing.JobType;
import fmi.ethnowear.persistence.jpa.entity.MediaAsset;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentProcessingJob;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPageMedia;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageMediaRepository;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WorkerJobInputService {

    private static final String PDF_MIME_TYPE = "application/pdf";

    private final MediaDeliveryService mediaDeliveryService;
    private final WorkerApiProperties properties;
    private final WorkerClaimedJobLoader jobLoader;
    private final DocumentPageMediaRepository pageMediaRepository;

    @Transactional
    public MediaDelivery findInput(
            Long jobId,
            WorkerClaimCredentials credentials
    ) {
        if (jobId == null)
            throw new IllegalArgumentException("Job id is required");

        DocumentProcessingJob job = jobLoader.requireActive(jobId, credentials);

        if (job.getStatus() == JobStatus.CANCEL_REQUESTED)
            throw new WorkerClaimConflictException();

        MediaAsset input = switch (job.getJobType()) {
            case PAGE_EXTRACTION -> getPdfInput(jobId, job);
            case OCR, EXTRACT_PAGE_FIGURES, OCR_QUALITY_ASSESSMENT,
                    VISION_OCR_ASSESSMENT ->
                    getPageRenditionInput(jobId, job);
            default -> throw new IllegalArgumentException("Job does not support input delivery");
        };

        return mediaDeliveryService.findById(input.getId());
    }

    private @NonNull MediaAsset getPdfInput(Long jobId, @NonNull DocumentProcessingJob job) {
        MediaAsset input = job.getInputMediaAsset();

        if (input == null)
            throw new ResourceNotFoundException("Job input media", jobId);

        if (input.getMediaType() != MediaType.PDF
                || !PDF_MIME_TYPE.equalsIgnoreCase(input.getMimeType()))
            throw new UnprocessableDocumentEvidenceException("Job input is not a supported PDF");

        if (input.getSizeBytes() == null
                || input.getSizeBytes() <= 0
                || input.getSizeBytes() > properties.maximumInputSize().toBytes())
            throw new UnprocessableDocumentEvidenceException("Job input size is invalid");
        return input;
    }

    private @NonNull MediaAsset getPageRenditionInput(Long jobId, @NonNull DocumentProcessingJob job) {
        if (job.getDocumentPage() == null)
            throw new UnprocessableDocumentEvidenceException("Page job has no document page");

        MediaAsset input = job.getInputMediaAsset();

        if (input == null)
            throw new ResourceNotFoundException("Job input media", jobId);

        DocumentPageMedia preferred = pageMediaRepository
                .findByDocumentPage_IdAndPreferredOcrInputTrue(
                        job.getDocumentPage().getId()
                )
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Preferred OCR input",
                        job.getDocumentPage().getId()
                ));

        if (preferred.getMediaAsset() == null
                || !input.getId().equals(preferred.getMediaAsset().getId()))
            throw new UnprocessableDocumentEvidenceException(
                    "Job input is not the page's preferred rendition"
            );

        if (input.getMediaType() != MediaType.IMAGE
                || input.getMimeType() == null
                || !input.getMimeType().toLowerCase().startsWith("image/"))
            throw new UnprocessableDocumentEvidenceException(
                    "Job input is not a supported page image"
            );

        if (input.getSizeBytes() == null
                || input.getSizeBytes() <= 0
                || input.getSizeBytes() > properties.maximumInputSize().toBytes())
            throw new UnprocessableDocumentEvidenceException("Job input size is invalid");

        return input;
    }
}
