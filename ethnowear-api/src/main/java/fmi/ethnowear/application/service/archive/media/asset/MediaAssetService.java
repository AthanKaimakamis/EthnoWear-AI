package fmi.ethnowear.application.service.archive.media.asset;

import fmi.ethnowear.application.dto.archive.media.MediaAssetDetails;
import fmi.ethnowear.application.dto.archive.media.MediaAssetMetadataWriteDto;
import fmi.ethnowear.application.dto.archive.media.DocumentFigureMediaLinkDetails;
import fmi.ethnowear.application.exception.ResourceInUseException;
import fmi.ethnowear.application.exception.ResourceNotFoundException;
import fmi.ethnowear.application.service.archive.media.storage.MediaPathResolver;
import fmi.ethnowear.domain.model.document.figure.FigureReviewState;
import fmi.ethnowear.domain.model.media.MediaOrigin;
import fmi.ethnowear.persistence.jpa.entity.MediaAsset;
import fmi.ethnowear.persistence.jpa.entity.SourceReference;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPageFigure;
import fmi.ethnowear.persistence.jpa.repository.MediaAssetRepository;
import fmi.ethnowear.persistence.jpa.repository.SourceReferenceRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageFigureRepository;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;
import java.util.stream.Collectors;

import static fmi.ethnowear.util.IdentifierUtils.requireId;
import static fmi.ethnowear.util.TextUtils.isBlank;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MediaAssetService {

    private final MediaAssetRepository mediaAssetRepository;
    private final DocumentPageFigureRepository figureRepository;
    private final SourceReferenceRepository sourceReferenceRepository;
    private final MediaAssetMapper mediaAssetMapper;
    private final MediaAssetUsageChecker usageChecker;
    private final MediaPathResolver paths;

    public Page<MediaAssetDetails> findAll(Pageable pageable) {
        Page<MediaAsset> assets = mediaAssetRepository.findVisibleInGeneralLibrary(
                        MediaOrigin.GENERATED,
                        FigureReviewState.APPROVED,
                        pageable
                );
        Map<Long, DocumentFigureMediaLinkDetails> figureLinks = approvedFigureLinks(
                assets.stream().map(MediaAsset::getId).toList()
        );

        return assets.map(asset -> mediaAssetMapper.toDetails(
                asset,
                figureLinks.get(asset.getId())
        ));
    }

    public MediaAssetDetails findById(Long id) {
        MediaAsset asset = requireLibraryAsset(id);
        return mediaAssetMapper.toDetails(asset, approvedFigureLink(id));
    }

    @Transactional
    public MediaAssetDetails updateMetadata(Long id, MediaAssetMetadataWriteDto input) {
        if(input == null)
            throw new IllegalArgumentException("Media asset metadata is required");

        MediaAsset asset = requireAsset(id);
        SourceReference sourceReference = requireSourceReference(input.sourceReferenceId());
        mediaAssetMapper.applyMetadata(asset, input, sourceReference);

        MediaAsset saved = mediaAssetRepository.save(asset);
        return mediaAssetMapper.toDetails(saved, approvedFigureLink(saved.getId()));
    }

    @Transactional
    public void delete(Long id) {
        MediaAsset asset = requireAsset(id);

        if (usageChecker.isInUse(id))
            throw new ResourceInUseException("Media asset", id);

        mediaAssetRepository.delete(asset);
        mediaAssetRepository.flush();

        deleteManagedFile(asset.getFilePath());
        deleteManagedFile(asset.getThumbnailPath());
    }

    private SourceReference requireSourceReference(Long id) {
        if(id == null)
            return null;

        requireId(id, "Source reference");

        return sourceReferenceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Source reference", id));
    }

    private @NonNull MediaAsset requireAsset(Long id) {
        requireId(id, "Media asset");

        return mediaAssetRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Media asset", id));
    }

    private @NonNull MediaAsset requireLibraryAsset(Long id) {
        requireId(id, "Media asset");

        return mediaAssetRepository.findVisibleInGeneralLibraryById(
                        id,
                        MediaOrigin.GENERATED,
                        FigureReviewState.APPROVED
                )
                .orElseThrow(() -> new ResourceNotFoundException("Media asset", id));
    }

    private DocumentFigureMediaLinkDetails approvedFigureLink(Long mediaAssetId) {
        return figureRepository
                .findFirstByMediaAsset_IdAndReviewStateOrderByIdAsc(
                        mediaAssetId,
                        FigureReviewState.APPROVED
                )
                .map(this::toFigureLink)
                .orElse(null);
    }

    private Map<Long, DocumentFigureMediaLinkDetails> approvedFigureLinks(
            java.util.Collection<Long> mediaAssetIds
    ) {
        if (mediaAssetIds.isEmpty())
            return Map.of();

        return figureRepository
                .findByMediaAsset_IdInAndReviewState(
                        mediaAssetIds,
                        FigureReviewState.APPROVED
                )
                .stream()
                .collect(Collectors.toMap(
                        figure -> figure.getMediaAsset().getId(),
                        this::toFigureLink,
                        (first, ignored) -> first
                ));
    }

    private DocumentFigureMediaLinkDetails toFigureLink(DocumentPageFigure figure) {
        String caption = isBlank(figure.getCorrectedCaptionText())
                ? figure.getRawCaptionText()
                : figure.getCorrectedCaptionText();

        return new DocumentFigureMediaLinkDetails(
                figure.getDocumentPage().getDocument().getId(),
                figure.getDocumentPage().getId(),
                figure.getDocumentPage().getPageSequence(),
                figure.getId(),
                caption,
                figure.getPrintedFigureNumber(),
                figure.getSourceReference() == null
                        ? null
                        : figure.getSourceReference().getId(),
                figure.getReviewState()
        );
    }

    private void deleteManagedFile(String relativePath) {
        if (isBlank(relativePath)) return;
        try {
            Files.deleteIfExists(paths.resolveExisting(relativePath));
        } catch (IOException ex) {
            throw new IllegalStateException("Could not delete managed media file", ex);
        }
    }

}
