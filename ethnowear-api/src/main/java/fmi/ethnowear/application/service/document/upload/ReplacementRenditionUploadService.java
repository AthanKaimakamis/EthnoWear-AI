package fmi.ethnowear.application.service.document.upload;

import fmi.ethnowear.application.dto.archive.media.MediaAssetDetails;
import fmi.ethnowear.application.dto.archive.media.MediaUploadRequest;
import fmi.ethnowear.application.dto.document.command.upload.DocumentUploadDetails;
import fmi.ethnowear.application.dto.document.command.upload.ReplacementRenditionUploadCommand;
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

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import static fmi.ethnowear.util.IdentifierUtils.requireId;

@Service
@RequiredArgsConstructor
public class ReplacementRenditionUploadService {

    private final DocumentRepository documentRepository;
    private final DocumentPageRepository documentPageRepository;
    private final DocumentPageMediaRepository documentPageMediaRepository;
    private final MediaAssetRepository mediaAssetRepository;
    private final MediaUploadService mediaUploadService;
    private final DocumentUploadValidator validator;
    private final DocumentUploadReferenceResolver referenceResolver;
    private final DocumentPageMediaFactory pageMediaFactory;
    private final DocumentProcessingJobScheduler jobScheduler;
    private final DocumentUploadStatePolicy statePolicy;

    @Transactional
    public @NonNull DocumentUploadDetails upload(
            Long documentId,
            Long pageId,
            @NonNull ReplacementRenditionUploadCommand command,
            @NonNull MultipartFile file
    ) {
        requireId(documentId, "Document");
        requireId(pageId, "Document page");
        validator.validate(command);

        DocumentPage page = documentPageRepository
                .findByIdAndDocument_Id(pageId, documentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Document page",
                        pageId
                ));

        Document document = page.getDocument();

        SourceReference sourceReference;
        sourceReference = resolveSourceReference(page, command.sourceReferenceId());

        referenceResolver.requireDocumentSource(document, sourceReference);

        List<DocumentPageMedia> existingRenditions = documentPageMediaRepository
                .findByDocumentPage_IdOrderByDisplayOrderAscIdAsc(pageId);

        if (existingRenditions.isEmpty())
            throw new IllegalStateException("Document page has no original rendition");

        MediaAssetDetails uploadedMedia = mediaUploadService.upload(
                file,
                new MediaUploadRequest(
                        sourceReferenceId(sourceReference),
                        MediaType.SCAN,
                        null,
                        command.mediaDescription()
                ),
                MediaUploadDestination.documentPages(documentId)
        );

        MediaAsset mediaAsset = mediaAssetRepository.getReferenceById(uploadedMedia.id());

        if (command.preferredOcrInput())
            clearPreferredOcrInput(existingRenditions);

        DocumentPageMedia replacement =
                pageMediaFactory.createReplacement(
                        page,
                        mediaAsset,
                        command.preferredOcrInput(),
                        nextDisplayOrder(existingRenditions),
                        command.renditionNotes()
                );

        replacement = documentPageMediaRepository.saveAndFlush(replacement);

        DocumentProcessingJob processingJob = null;

        if (command.queueOcr()) {
            statePolicy.markPageReprocessing(document, page);

            processingJob = jobScheduler.queueOcr(page, mediaAsset);
        }

        documentRepository.save(document);
        documentPageRepository.save(page);

        return new DocumentUploadDetails(
                document.getId(),
                mediaAsset.getId(),
                page.getId(),
                replacement.getId(),
                processingJob == null
                        ? null
                        : processingJob.getId()
        );
    }

    private SourceReference resolveSourceReference(DocumentPage page, Long sourceReferenceId) {
        if (sourceReferenceId == null)
            return page.getSourceReference();

        return referenceResolver.sourceReference(sourceReferenceId);
    }

    private void clearPreferredOcrInput(@NonNull List<DocumentPageMedia> renditions) {
        List<DocumentPageMedia> preferred = renditions.stream()
                .filter(DocumentPageMedia::isPreferredOcrInput)
                .toList();

        preferred.forEach(rendition -> rendition.setPreferredOcrInput(false));

        if (preferred.isEmpty())
            return;

        documentPageMediaRepository.saveAll(preferred);
        documentPageMediaRepository.flush();
    }

    private int nextDisplayOrder(@NonNull List<DocumentPageMedia> renditions) {
        return renditions.stream()
                .map(DocumentPageMedia::getDisplayOrder)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .map(displayOrder -> displayOrder + 1)
                .orElse(0);
    }

    private Long sourceReferenceId(SourceReference sourceReference) {
        return sourceReference == null
                ? null
                : sourceReference.getId();
    }
}
