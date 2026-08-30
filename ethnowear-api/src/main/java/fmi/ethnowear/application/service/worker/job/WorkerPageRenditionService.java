package fmi.ethnowear.application.service.worker.job;

import fmi.ethnowear.application.dto.archive.media.*;
import fmi.ethnowear.application.dto.worker.rendition.*;
import fmi.ethnowear.application.exception.*;
import fmi.ethnowear.application.model.worker.WorkerClaimCredentials;
import fmi.ethnowear.application.model.event.ManagementEvent;
import fmi.ethnowear.application.service.archive.media.storage.*;
import fmi.ethnowear.application.service.event.ManagementEventPublisher;
import fmi.ethnowear.application.service.worker.security.WorkerClaimedJobLoader;
import fmi.ethnowear.config.WorkerApiProperties;
import fmi.ethnowear.domain.model.archive.MediaType;
import fmi.ethnowear.domain.model.document.processing.*;
import fmi.ethnowear.persistence.jpa.entity.MediaAsset;
import fmi.ethnowear.persistence.jpa.entity.document.*;
import fmi.ethnowear.persistence.jpa.repository.MediaAssetRepository;
import fmi.ethnowear.persistence.jpa.repository.document.*;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;

@Service
@RequiredArgsConstructor
public class WorkerPageRenditionService {

    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp"
    );

    private final WorkerClaimedJobLoader jobLoader;
    private final DocumentPageRepository pageRepository;
    private final DocumentPageMediaRepository pageMediaRepository;
    private final MediaAssetRepository mediaAssetRepository;
    private final MediaUploadService uploadService;
    private final MediaFileHasher fileHasher;
    private final WorkerApiProperties properties;
    private final ManagementEventPublisher managementEvents;

    @Transactional
    public WorkerPageRenditionDetails upload(
            Long jobId,
            Long pageId,
            WorkerClaimCredentials credentials,
            WorkerPageRenditionCommand command,
            MultipartFile file
    ) {
        validateUpload(command, file);

        DocumentProcessingJob job = jobLoader.requireActive(jobId, credentials);

        if(job.getStatus() == JobStatus.CANCEL_REQUESTED)
            throw new WorkerClaimConflictException();

        if(job.getJobType() != JobType.PAGE_EXTRACTION || job.getDocument() == null)
            throw new WorkerRenditionConflictException(
                    "Job is not a document page-extraction job"
            );

        DocumentPage page = pageRepository.findByIdAndDocument_Id(
                pageId,
                job.getDocument().getId()
        ).orElseThrow(() -> new ResourceNotFoundException(
                "Document page",
                pageId
        ));

        if (job.getDocumentPage() != null
                && !Objects.equals(job.getDocumentPage().getId(), page.getId()))
            throw new WorkerRenditionConflictException(
                    "Rendition page does not match the targeted extraction job"
            );

        validatePageIdentity(page, command);

        String checksum = checksum(file);

        Optional<DocumentPageMedia> existing =
                pageMediaRepository
                        .findByDocumentPage_IdAndRenditionTypeAndProducingJob_IdAndProducingAttempt(
                                pageId,
                                command.renditionType().toDomainType(),
                                jobId,
                                job.getAttemptCount()
                        );

        if(existing.isPresent())
            return existingResult(existing.get(), checksum, command.renditionType());

        MediaAssetDetails uploaded = uploadService.upload(
                file,
                new MediaUploadRequest(
                        null,
                        MediaType.IMAGE,
                        null,
                        null
                ),
                MediaUploadDestination.documentPageRendition(
                        job.getDocument().getId(),
                        pageId
                )
        );

        MediaAsset mediaAsset = mediaAssetRepository.findById(uploaded.id())
                .orElseThrow(() -> new IllegalStateException(
                        "Uploaded media asset was not persisted"
                ));

        validateStoredMedia(mediaAsset, checksum, command);

        List<DocumentPageMedia> existingPageMedia =
                pageMediaRepository.findByDocumentPage_IdOrderByDisplayOrderAscIdAsc(pageId);

        DocumentPageMedia rendition = new DocumentPageMedia();
        rendition.setDocumentPage(page);
        rendition.setMediaAsset(mediaAsset);
        rendition.setRenditionType(command.renditionType().toDomainType());
        rendition.setOriginal(false);
        rendition.setPreferredOcrInput(
                existingPageMedia.stream()
                        .noneMatch(DocumentPageMedia::isPreferredOcrInput)
        );
        rendition.setDisplayOrder(nextDisplayOrder(existingPageMedia));
        rendition.setWidth(mediaAsset.getWidth());
        rendition.setHeight(mediaAsset.getHeight());
        rendition.setDpi(command.dpi());
        rendition.setColorMode(command.colorMode().name());
        rendition.setRenditionHash(checksum);
        rendition.setNotes(rendererDescription(command));
        rendition.assignProducingAttempt(job, job.getAttemptCount());

        rendition = pageMediaRepository.saveAndFlush(rendition);
        managementEvents.media(
                mediaAsset,
                ManagementEvent.Action.CREATED
        );
        managementEvents.page(
                page,
                ManagementEvent.Action.UPDATED
        );

        return details(rendition, command.renditionType(), false);
    }

    private void validateUpload(
            WorkerPageRenditionCommand command,
            MultipartFile file
    ) {
        if(command == null)
            throw new IllegalArgumentException("Rendition metadata is required");

        if(file == null || file.isEmpty())
            throw new IllegalArgumentException("A non-empty rendition file is required");

        if(file.getSize() > properties.maximumRenditionSize().toBytes())
            throw new WorkerPayloadTooLargeException("Rendition exceeds the maximum size");

        if(file.getContentType() == null
                || !ALLOWED_MIME_TYPES.contains(
                file.getContentType().toLowerCase(Locale.ROOT)
        ))
            throw new WorkerUnsupportedMediaTypeException("Unsupported rendition media type");

        if(command.dpi() == null || command.dpi() != properties.renderDpi())
            throw new IllegalArgumentException("Rendition DPI does not match the configured DPI");

        if(command.pixelWidth() == null
                || command.pixelWidth() > properties.maximumPixelWidth()
                || command.pixelHeight() == null
                || command.pixelHeight() > properties.maximumPixelHeight()
                || (long) command.pixelWidth() * command.pixelHeight() > properties.maximumPagePixels())
            throw new IllegalArgumentException("Rendition dimensions exceed configured limits");
    }

    private void validatePageIdentity(
            @NonNull DocumentPage page,
            @NonNull WorkerPageRenditionCommand command
    ) {
        if(!Objects.equals(page.getPdfPageIndex(), command.pdfPageIndex())
                || !Objects.equals(page.getPageSequence(), command.pageSequence()))
            throw new WorkerRenditionConflictException(
                    "Rendition metadata does not match the document page"
            );
    }

    private String checksum(@NonNull MultipartFile file) {
        try {
            return fileHasher.sha256(file.getInputStream());
        } catch(IOException ex) {
            throw new IllegalStateException("Could not read rendition content", ex);
        }
    }

    private @NonNull WorkerPageRenditionDetails existingResult(
            @NonNull DocumentPageMedia existing,
            @NonNull String checksum,
            WorkerPageRenditionType renditionType
    ) {
        if(!checksum.equals(existing.getRenditionHash()))
            throw new WorkerRenditionConflictException(
                    "A different rendition already exists for this job attempt"
            );

        return details(existing, renditionType, true);
    }

    private void validateStoredMedia(
            @NonNull MediaAsset mediaAsset,
            @NonNull String checksum,
            WorkerPageRenditionCommand command
    ) {
        if(!checksum.equals(mediaAsset.getChecksum())
                || !Objects.equals(mediaAsset.getWidth(), command.pixelWidth())
                || !Objects.equals(mediaAsset.getHeight(), command.pixelHeight()))
            throw new WorkerRenditionConflictException(
                    "Stored rendition does not match its metadata"
            );
    }

    private int nextDisplayOrder(@NonNull List<DocumentPageMedia> renditions) {
        return renditions.stream()
                .map(DocumentPageMedia::getDisplayOrder)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .map(value -> value + 1)
                .orElse(0);
    }

    private @NonNull String rendererDescription(@NonNull WorkerPageRenditionCommand command) {
        if(command.rendererVersion() == null || command.rendererVersion().isBlank())
            return "Rendered by " + command.rendererName().trim();

        return "Rendered by "
                + command.rendererName().trim()
                + " "
                + command.rendererVersion().trim();
    }

    @Contract("_, _, _ -> new")
    private @NonNull WorkerPageRenditionDetails details(
            @NonNull DocumentPageMedia rendition,
            WorkerPageRenditionType type,
            boolean existing
    ) {
        return new WorkerPageRenditionDetails(
                rendition.getDocumentPage().getId(),
                rendition.getId(),
                rendition.getMediaAsset().getId(),
                type,
                existing
        );
    }
}
