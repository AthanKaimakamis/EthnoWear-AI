package fmi.ethnowear.application.service.worker.job;

import fmi.ethnowear.application.dto.archive.media.MediaAssetDetails;
import fmi.ethnowear.application.dto.archive.media.MediaUploadRequest;
import fmi.ethnowear.application.dto.worker.figure.WorkerFigureCandidateDetails;
import fmi.ethnowear.application.dto.worker.figure.WorkerFigureCropCommand;
import fmi.ethnowear.application.dto.worker.figure.WorkerFigureCropDetails;
import fmi.ethnowear.application.dto.worker.figure.WorkerFigureExtractionContextDetails;
import fmi.ethnowear.application.exception.ResourceNotFoundException;
import fmi.ethnowear.application.exception.WorkerClaimConflictException;
import fmi.ethnowear.application.exception.WorkerFigureConflictException;
import fmi.ethnowear.application.exception.WorkerPayloadTooLargeException;
import fmi.ethnowear.application.exception.WorkerUnsupportedMediaTypeException;
import fmi.ethnowear.application.model.event.ManagementEvent;
import fmi.ethnowear.application.model.worker.WorkerClaimCredentials;
import fmi.ethnowear.application.service.archive.media.storage.MediaFileHasher;
import fmi.ethnowear.application.service.archive.media.storage.MediaUploadDestination;
import fmi.ethnowear.application.service.archive.media.storage.MediaUploadService;
import fmi.ethnowear.application.service.document.processing.DocumentProcessingJobKeyFactory;
import fmi.ethnowear.application.service.event.ManagementEventPublisher;
import fmi.ethnowear.application.service.worker.security.WorkerClaimedJobLoader;
import fmi.ethnowear.config.FigureExtractionProperties;
import fmi.ethnowear.domain.model.archive.MediaType;
import fmi.ethnowear.domain.model.document.figure.FigureReviewState;
import fmi.ethnowear.domain.model.document.processing.JobStatus;
import fmi.ethnowear.domain.model.document.processing.JobType;
import fmi.ethnowear.persistence.jpa.entity.MediaAsset;
import fmi.ethnowear.persistence.jpa.entity.SourceReference;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPage;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPageFigure;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPageFigureCandidate;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPageOcrResult;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentProcessingJob;
import fmi.ethnowear.persistence.jpa.repository.MediaAssetRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageFigureCandidateRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageFigureRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageOcrResultRepository;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class WorkerFigureExtractionService {

    private static final Set<String> ALLOWED_CROP_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp"
    );

    private final WorkerClaimedJobLoader jobLoader;
    private final DocumentProcessingJobKeyFactory jobKeyFactory;
    private final DocumentPageOcrResultRepository ocrResultRepository;
    private final DocumentPageFigureCandidateRepository candidateRepository;
    private final DocumentPageFigureRepository figureRepository;
    private final MediaAssetRepository mediaAssetRepository;
    private final MediaUploadService uploadService;
    private final MediaFileHasher fileHasher;
    private final FigureExtractionProperties properties;
    private final ManagementEventPublisher managementEvents;

    @Transactional(readOnly = true)
    public WorkerFigureExtractionContextDetails context(
            Long jobId,
            WorkerClaimCredentials credentials
    ) {
        DocumentProcessingJob job = requireFigureJob(jobId, credentials);
        Evidence evidence = evidence(job);
        List<WorkerFigureCandidateDetails> candidates = candidateRepository
                .findByDocumentPageOcrResult_IdOrderByCandidateOrdinalAsc(
                        evidence.ocrResult().getId()
                ).stream()
                .map(this::candidateDetails)
                .toList();

        if (candidates.isEmpty())
            throw new WorkerFigureConflictException(
                    "Figure-extraction job has no candidates"
            );

        return new WorkerFigureExtractionContextDetails(
                job.getId(),
                evidence.page().getDocument().getId(),
                evidence.page().getId(),
                evidence.ocrResult().getDocumentPageMedia().getId(),
                job.getInputMediaAsset().getId(),
                evidence.ocrResult().getId(),
                evidence.ocrResult().getStructuredOutputJson(),
                candidates,
                properties.maximumCaptionCharacters(),
                properties.maximumPrintedNumberCharacters(),
                properties.maximumCropSize().toBytes()
        );
    }

    @Transactional
    public WorkerFigureCropDetails upload(
            Long jobId,
            WorkerClaimCredentials credentials,
            WorkerFigureCropCommand command,
            MultipartFile file
    ) {
        validateUpload(command, file);
        DocumentProcessingJob job = requireFigureJob(jobId, credentials);
        Evidence evidence = evidence(job);
        DocumentPageFigureCandidate candidate = candidateRepository
                .findByIdAndDocumentPageOcrResult_Id(
                        command.candidateId(),
                        evidence.ocrResult().getId()
                )
                .orElseThrow(() -> new WorkerFigureConflictException(
                        "Figure candidate does not belong to the claimed OCR result"
                ));
        String checksum = checksum(file);

        Optional<DocumentPageFigure> existing = figureRepository
                .findByProcessingJob_IdAndProducingAttemptAndFigureOrdinal(
                        jobId,
                        job.getAttemptCount(),
                        command.figureOrdinal()
                );

        if (existing.isPresent())
            return existing(existing.get(), candidate, command, checksum);

        SourceReference sourceReference = evidence.page().getSourceReference();
        if (sourceReference == null)
            sourceReference = evidence.page().getDocument().getDefaultSourceReference();

        MediaAssetDetails uploaded = uploadService.upload(
                file,
                new MediaUploadRequest(
                        sourceReference == null ? null : sourceReference.getId(),
                        MediaType.IMAGE,
                        null,
                        null
                ),
                MediaUploadDestination.documentPageFigure(
                        evidence.page().getDocument().getId(),
                        evidence.page().getId()
                )
        );
        MediaAsset media = mediaAssetRepository.findById(uploaded.id())
                .orElseThrow(() -> new IllegalStateException(
                        "Uploaded figure media was not persisted"
                ));

        if (!checksum.equals(media.getChecksum()))
            throw new WorkerFigureConflictException(
                    "Stored figure crop does not match the uploaded content"
            );

        DocumentPageFigure figure = new DocumentPageFigure();
        figure.setDocumentPage(evidence.page());
        figure.setDocumentPageMedia(evidence.ocrResult().getDocumentPageMedia());
        figure.setMediaAsset(media);
        figure.setSourceReference(sourceReference);
        figure.setFigureCandidate(candidate);
        figure.setFigureOrdinal(command.figureOrdinal());
        figure.setPrintedFigureNumber(normalize(command.printedFigureNumber()));
        figure.setNormalizedX(candidate.getNormalizedX());
        figure.setNormalizedY(candidate.getNormalizedY());
        figure.setNormalizedWidth(candidate.getNormalizedWidth());
        figure.setNormalizedHeight(candidate.getNormalizedHeight());
        figure.setRawCaptionText(normalize(command.rawCaptionText()));
        figure.setReviewState(FigureReviewState.PENDING);
        figure.setDetectionConfidence(candidate.getDetectionConfidence());
        figure.setProcessingJob(job);
        figure.setProducingAttempt(job.getAttemptCount());

        figure = figureRepository.saveAndFlush(figure);
        managementEvents.media(media, ManagementEvent.Action.CREATED);
        managementEvents.page(evidence.page(), ManagementEvent.Action.UPDATED);
        return details(figure, false);
    }

    private DocumentProcessingJob requireFigureJob(
            Long jobId,
            WorkerClaimCredentials credentials
    ) {
        DocumentProcessingJob job = jobLoader.requireActive(jobId, credentials);

        if (job.getStatus() == JobStatus.CANCEL_REQUESTED)
            throw new WorkerClaimConflictException();

        if (job.getJobType() != JobType.EXTRACT_PAGE_FIGURES)
            throw new IllegalArgumentException("Job is not a figure-extraction job");

        return job;
    }

    private Evidence evidence(DocumentProcessingJob job) {
        DocumentPage page = job.getDocumentPage();
        Long ocrResultId = jobKeyFactory.figureOcrResultId(job.getJobKey());
        DocumentPageOcrResult result = ocrResultRepository.findById(ocrResultId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "OCR result",
                        ocrResultId
                ));

        if (page == null
                || page.getDocument() == null
                || !result.isCurrent()
                || result.getDocumentPage() == null
                || !Objects.equals(result.getDocumentPage().getId(), page.getId())
                || result.getDocumentPageMedia() == null
                || result.getDocumentPageMedia().getMediaAsset() == null
                || job.getInputMediaAsset() == null
                || !Objects.equals(
                result.getDocumentPageMedia().getMediaAsset().getId(),
                job.getInputMediaAsset().getId()
        ))
            throw new WorkerFigureConflictException(
                    "Figure-extraction evidence is stale or mismatched"
            );

        return new Evidence(page, result);
    }

    private void validateUpload(
            WorkerFigureCropCommand command,
            MultipartFile file
    ) {
        if (command == null)
            throw new IllegalArgumentException("Figure metadata is required");

        if (file == null || file.isEmpty())
            throw new IllegalArgumentException("A non-empty figure crop is required");

        if (file.getSize() > properties.maximumCropSize().toBytes())
            throw new WorkerPayloadTooLargeException(
                    "Figure crop exceeds the configured maximum"
            );

        if (file.getContentType() == null
                || !ALLOWED_CROP_TYPES.contains(
                file.getContentType().toLowerCase(Locale.ROOT)
        ))
            throw new WorkerUnsupportedMediaTypeException(
                    "Unsupported figure crop media type"
            );

        if (length(command.rawCaptionText()) > properties.maximumCaptionCharacters())
            throw new IllegalArgumentException("Figure caption exceeds the configured maximum");

        if (length(command.printedFigureNumber())
                > properties.maximumPrintedNumberCharacters())
            throw new IllegalArgumentException(
                    "Printed figure number exceeds the configured maximum"
            );
    }

    private WorkerFigureCropDetails existing(
            DocumentPageFigure figure,
            DocumentPageFigureCandidate candidate,
            WorkerFigureCropCommand command,
            String checksum
    ) {
        if (figure.getFigureCandidate() == null
                || !Objects.equals(figure.getFigureCandidate().getId(), candidate.getId())
                || !Objects.equals(figure.getPrintedFigureNumber(), normalize(command.printedFigureNumber()))
                || !Objects.equals(figure.getRawCaptionText(), normalize(command.rawCaptionText()))
                || figure.getMediaAsset() == null
                || !Objects.equals(figure.getMediaAsset().getChecksum(), checksum))
            throw new WorkerFigureConflictException(
                    "A different figure crop already exists for this job attempt"
            );

        return details(figure, true);
    }

    private WorkerFigureCandidateDetails candidateDetails(
            DocumentPageFigureCandidate candidate
    ) {
        return new WorkerFigureCandidateDetails(
                candidate.getId(),
                candidate.getCandidateOrdinal(),
                candidate.getNormalizedX(),
                candidate.getNormalizedY(),
                candidate.getNormalizedWidth(),
                candidate.getNormalizedHeight(),
                candidate.getRawCaptionText(),
                candidate.getDetectionConfidence()
        );
    }

    private WorkerFigureCropDetails details(DocumentPageFigure figure, boolean existing) {
        return new WorkerFigureCropDetails(
                figure.getId(),
                figure.getDocumentPage().getId(),
                figure.getMediaAsset().getId(),
                figure.getFigureOrdinal(),
                existing
        );
    }

    private String checksum(MultipartFile file) {
        try {
            return fileHasher.sha256(file.getInputStream());
        } catch (IOException ex) {
            throw new IllegalStateException("Could not read figure crop", ex);
        }
    }

    private int length(String value) {
        return value == null ? 0 : value.trim().length();
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record Evidence(
            @NonNull DocumentPage page,
            @NonNull DocumentPageOcrResult ocrResult
    ) {
    }
}
