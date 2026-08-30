package fmi.ethnowear.application.service.document.upload;

import fmi.ethnowear.application.dto.archive.media.MediaAssetDetails;
import fmi.ethnowear.application.dto.archive.media.MediaUploadRequest;
import fmi.ethnowear.application.dto.document.command.upload.DocumentUploadDetails;
import fmi.ethnowear.application.dto.document.command.upload.MissingPageUploadCommand;
import fmi.ethnowear.application.exception.ResourceNotFoundException;
import fmi.ethnowear.application.service.archive.media.storage.MediaUploadDestination;
import fmi.ethnowear.application.service.archive.media.storage.MediaUploadService;
import fmi.ethnowear.application.service.document.processing.DocumentProcessingJobScheduler;
import fmi.ethnowear.domain.model.archive.MediaType;
import fmi.ethnowear.persistence.jpa.entity.MediaAsset;
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
public class MissingPageUploadService {

    private final DocumentRepository documentRepository;
    private final DocumentPageRepository documentPageRepository;
    private final DocumentPageMediaRepository documentPageMediaRepository;
    private final MediaAssetRepository mediaAssetRepository;
    private final MediaUploadService mediaUploadService;
    private final DocumentUploadValidator validator;
    private final DocumentUploadReferenceResolver referenceResolver;
    private final DocumentPageFactory pageFactory;
    private final DocumentPageMediaFactory pageMediaFactory;
    private final DocumentPageProvenanceRecorder provenanceRecorder;
    private final DocumentProcessingJobScheduler jobScheduler;
    private final DocumentUploadStatePolicy statePolicy;

    @Transactional
    public @NonNull DocumentUploadDetails upload(
            Long documentId,
            @NonNull MissingPageUploadCommand command,
            @NonNull MultipartFile file
    ) {
        validator.validate(command);

        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document", documentId));

        requireAvailablePageSequence(documentId, command.pageSequence());

        SourceReference sourceReference = referenceResolver.sourceReference(
                command.provenance().sourceReferenceId()
        );

        referenceResolver.requireDocumentSource(document, sourceReference);

        MediaAssetDetails uploadedMedia = mediaUploadService.upload(
                file,
                new MediaUploadRequest(
                        sourceReferenceId(sourceReference),
                        MediaType.IMAGE,
                        null,
                        command.mediaDescription()
                ),
                MediaUploadDestination.documentPages(documentId)
        );

        MediaAsset mediaAsset = mediaAssetRepository.getReferenceById(uploadedMedia.id());

        DocumentPage page = pageFactory.createMissingPage(
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
                command.renditionNotes()
        );

        pageMedia = documentPageMediaRepository.saveAndFlush(pageMedia);

        provenanceRecorder.recordInitial(page, sourceReference, command.provenance());

        statePolicy.markPageAdded(document, command.queueOcr());

        document.setPageCount(Math.toIntExact(documentPageRepository.countByDocument_Id(documentId)));

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

    private void requireAvailablePageSequence(Long documentId, int pageSequence) {
        if (documentPageRepository
                .findByDocument_IdAndPageSequence(
                        documentId,
                        pageSequence
                )
                .isPresent())
            throw new IllegalArgumentException("Document page sequence already exists: " + pageSequence);
    }

    private Long sourceReferenceId(SourceReference sourceReference) {
        return sourceReference == null
                ? null
                : sourceReference.getId();
    }
}
