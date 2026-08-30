package fmi.ethnowear.application.service.document.upload;

import fmi.ethnowear.application.dto.archive.media.MediaAssetDetails;
import fmi.ethnowear.application.dto.archive.media.MediaUploadRequest;
import fmi.ethnowear.application.exception.ResourceNotFoundException;
import fmi.ethnowear.application.service.archive.media.storage.MediaUploadDestination;
import fmi.ethnowear.application.service.archive.media.storage.MediaUploadService;
import fmi.ethnowear.domain.model.archive.MediaType;
import fmi.ethnowear.persistence.jpa.entity.MediaAsset;
import fmi.ethnowear.persistence.jpa.entity.document.Document;
import fmi.ethnowear.persistence.jpa.repository.MediaAssetRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentRepository;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class DocumentThumbnailService {

    private final DocumentRepository documentRepository;
    private final MediaAssetRepository mediaAssetRepository;
    private final MediaUploadService mediaUploadService;

    @Transactional
    public MediaAssetDetails upload(Long documentId, @NonNull MultipartFile file) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document", documentId));

        return uploadAndAssign(document, file);
    }

    public MediaAssetDetails uploadAndAssign(
            @NonNull Document document,
            @NonNull MultipartFile file
    ) {
        MediaAssetDetails uploaded = mediaUploadService.upload(
                file,
                new MediaUploadRequest(null, MediaType.THUMBNAIL, null, "Document thumbnail"),
                MediaUploadDestination.documentThumbnail(document.getId())
        );
        MediaAsset asset = mediaAssetRepository.findById(uploaded.id())
                .orElseThrow(() -> new IllegalStateException(
                        "Uploaded media asset was not persisted: " + uploaded.id()
                ));

        document.setThumbnailMediaAsset(asset);
        documentRepository.save(document);
        return uploaded;
    }
}
