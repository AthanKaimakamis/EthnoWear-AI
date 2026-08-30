package fmi.ethnowear.application.service.document.upload;

import fmi.ethnowear.application.dto.archive.media.MediaAssetDetails;
import fmi.ethnowear.application.dto.archive.media.MediaUploadRequest;
import fmi.ethnowear.application.dto.document.command.upload.DocumentUploadDetails;
import fmi.ethnowear.application.dto.document.command.upload.StandaloneCaptureUploadCommand;
import fmi.ethnowear.application.service.archive.media.storage.MediaUploadDestination;
import fmi.ethnowear.application.service.archive.media.storage.MediaUploadService;
import fmi.ethnowear.application.service.document.processing.DocumentProcessingJobScheduler;
import fmi.ethnowear.domain.model.archive.MediaType;
import fmi.ethnowear.domain.model.document.DocumentType;
import fmi.ethnowear.persistence.jpa.entity.MediaAsset;
import fmi.ethnowear.persistence.jpa.entity.Source;
import fmi.ethnowear.persistence.jpa.entity.SourceReference;
import fmi.ethnowear.persistence.jpa.entity.document.Document;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPage;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPageMedia;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentProcessingJob;
import fmi.ethnowear.persistence.jpa.repository.MediaAssetRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageMediaRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentRepository;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
public class StandaloneCaptureUploadService {

    private final DocumentRepository documentRepository;
    private final DocumentPageRepository documentPageRepository;
    private final DocumentPageMediaRepository documentPageMediaRepository;
    private final MediaAssetRepository mediaAssetRepository;
    private final MediaUploadService mediaUploadService;
    private final DocumentUploadValidator validator;
    private final DocumentUploadReferenceResolver referenceResolver;
    private final DocumentCreationFactory documentFactory;
    private final DocumentPageFactory pageFactory;
    private final DocumentPageMediaFactory pageMediaFactory;
    private final DocumentPageProvenanceRecorder provenanceRecorder;
    private final DocumentProcessingJobScheduler jobScheduler;
    private final DocumentUploadStatePolicy statePolicy;
    private final DocumentThumbnailService thumbnailService;

    @Transactional
    public @NonNull DocumentUploadDetails upload(
            @NonNull StandaloneCaptureUploadCommand command,
            @NonNull MultipartFile file
    ) {
        return upload(command, file, null);
    }

    @Transactional
    public @NonNull DocumentUploadDetails upload(
            @NonNull StandaloneCaptureUploadCommand command,
            @NonNull MultipartFile file,
            MultipartFile thumbnail
    ) {
        validator.validate(command);

        SourceReference sourceReference = referenceResolver
                .sourceReference(command.provenance().sourceReferenceId());

        Source source = resolveSource(command, sourceReference);

        Document document = documentFactory.create(
                command.metadata(),
                DocumentType.STANDALONE_CAPTURE,
                command.provenance().provenanceStatus(),
                command.provenance().provenanceTrustState(),
                source,
                sourceReference
        );

        referenceResolver.requireDocumentSource(document, sourceReference);

        document.setPageCount(1);
        statePolicy.markPageAdded(document,command.queueOcr());

        document = documentRepository.saveAndFlush(document);

        MediaAssetDetails uploadedMedia = mediaUploadService.upload(
                file,
                new MediaUploadRequest(
                        sourceReferenceId(sourceReference),
                        MediaType.IMAGE,
                        null,
                        command.mediaDescription()
                ),
                MediaUploadDestination.captureOriginal(document.getId())
        );

        MediaAsset mediaAsset = mediaAssetRepository.getReferenceById(uploadedMedia.id());

        document.setOriginalMediaAsset(mediaAsset);
        if (thumbnail != null && !thumbnail.isEmpty())
            thumbnailService.uploadAndAssign(document, thumbnail);

        DocumentPage page = pageFactory.createStandalone(
                document,
                sourceReference,
                command
        );

        page.setProcessingState(statePolicy.processingState(command.queueOcr()));
        page.setProvenanceReviewedAt(LocalDateTime.now(ZoneOffset.UTC));

        page = documentPageRepository.saveAndFlush(page);

        DocumentPageMedia pageMedia = pageMediaFactory.createOriginal(
                page,
                mediaAsset,
                null
        );

        pageMedia = documentPageMediaRepository.saveAndFlush(pageMedia);

        provenanceRecorder.recordInitial(
                page,
                sourceReference,
                command.provenance()
        );

        DocumentProcessingJob processingJob = null;

        if (command.queueOcr())
            processingJob = jobScheduler.queueOcr(page, mediaAsset);

        documentRepository.save(document);

        return new DocumentUploadDetails(
                document.getId(),
                mediaAsset.getId(),
                page.getId(),
                pageMedia.getId(),
                processingJob == null
                        ? null
                        : processingJob.getId()
        );
    }


    private Source resolveSource(
            @NonNull StandaloneCaptureUploadCommand command,
            SourceReference sourceReference
    ) {
        Source source = referenceResolver.source(command.metadata().sourceId());

        if (source == null && sourceReference != null)
            return sourceReference.getSource();

        return source;
    }

    private Long sourceReferenceId(SourceReference sourceReference) {
        return sourceReference == null ? null : sourceReference.getId();
    }
}
