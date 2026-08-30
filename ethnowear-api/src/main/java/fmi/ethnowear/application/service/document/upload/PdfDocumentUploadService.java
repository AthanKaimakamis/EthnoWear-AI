package fmi.ethnowear.application.service.document.upload;

import fmi.ethnowear.application.dto.archive.media.MediaAssetDetails;
import fmi.ethnowear.application.dto.archive.media.MediaUploadRequest;
import fmi.ethnowear.application.dto.document.command.upload.DocumentUploadDetails;
import fmi.ethnowear.application.dto.document.command.upload.PdfDocumentUploadCommand;
import fmi.ethnowear.application.service.archive.media.storage.MediaUploadDestination;
import fmi.ethnowear.application.service.archive.media.storage.MediaUploadService;
import fmi.ethnowear.application.service.document.processing.DocumentProcessingJobScheduler;
import fmi.ethnowear.domain.model.archive.MediaType;
import fmi.ethnowear.persistence.jpa.entity.MediaAsset;
import fmi.ethnowear.persistence.jpa.entity.Source;
import fmi.ethnowear.persistence.jpa.entity.SourceReference;
import fmi.ethnowear.persistence.jpa.entity.document.Document;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentProcessingJob;
import fmi.ethnowear.persistence.jpa.repository.MediaAssetRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentRepository;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class PdfDocumentUploadService {

    private final DocumentRepository documentRepository;
    private final MediaAssetRepository mediaAssetRepository;
    private final MediaUploadService mediaUploadService;
    private final DocumentUploadValidator validator;
    private final DocumentUploadReferenceResolver referenceResolver;
    private final DocumentCreationFactory documentFactory;
    private final DocumentProcessingJobScheduler jobScheduler;
    private final DocumentUploadStatePolicy statePolicy;
    private final DocumentThumbnailService thumbnailService;

    @Transactional
    public @NonNull DocumentUploadDetails upload(
            @NonNull PdfDocumentUploadCommand command,
            @NonNull MultipartFile file
    ) {
        return upload(command, file, null);
    }

    @Transactional
    public @NonNull DocumentUploadDetails upload(
            @NonNull PdfDocumentUploadCommand command,
            @NonNull MultipartFile file,
            MultipartFile thumbnail
    ) {
        validator.validate(command);

        Source source = referenceResolver.source(
                command.metadata().sourceId()
        );
        SourceReference defaultSourceReference = referenceResolver
                .sourceReference(command.metadata().defaultSourceReferenceId());
        referenceResolver.requireSource(source, defaultSourceReference);

        Document document = documentFactory.create(
                command.metadata(),
                command.documentType(),
                command.provenanceStatus(),
                command.provenanceTrustState(),
                source,
                defaultSourceReference
        );

        document = documentRepository.saveAndFlush(document);

        MediaAssetDetails uploadedMedia = mediaUploadService.upload(
                file,
                new MediaUploadRequest(
                        null,
                        MediaType.PDF,
                        null,
                        command.mediaDescription()
                ),
                MediaUploadDestination.documentOriginal(document.getId())
        );

        MediaAsset originalMedia = mediaAssetRepository.findById(
                uploadedMedia.id()
        ).orElseThrow(() -> new IllegalStateException(
                "Uploaded media asset was not persisted: " + uploadedMedia.id()
        ));

        document.setOriginalMediaAsset(originalMedia);
        if (thumbnail != null && !thumbnail.isEmpty())
            thumbnailService.uploadAndAssign(document, thumbnail);
        document.setProcessingState(statePolicy.processingState(true));

        DocumentProcessingJob processingJob = jobScheduler
                .queuePageExtraction(document, originalMedia);

        documentRepository.save(document);

        return new DocumentUploadDetails(
                document.getId(),
                originalMedia.getId(),
                null,
                null,
                processingJob.getId()
        );
    }
}
