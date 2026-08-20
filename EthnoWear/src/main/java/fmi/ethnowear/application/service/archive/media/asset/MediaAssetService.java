package fmi.ethnowear.application.service.archive.media.asset;

import fmi.ethnowear.application.dto.archive.media.MediaAssetDetails;
import fmi.ethnowear.application.dto.archive.media.MediaAssetMetadataWriteDto;
import fmi.ethnowear.application.exception.ResourceInUseException;
import fmi.ethnowear.application.exception.ResourceNotFoundException;
import fmi.ethnowear.application.service.archive.media.storage.MediaPathResolver;
import fmi.ethnowear.persistence.jpa.entity.MediaAsset;
import fmi.ethnowear.persistence.jpa.entity.SourceReference;
import fmi.ethnowear.persistence.jpa.repository.MediaAssetRepository;
import fmi.ethnowear.persistence.jpa.repository.SourceReferenceRepository;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.io.IOException;
import java.nio.file.Files;

import static fmi.ethnowear.util.IdentifierUtils.requireId;
import static fmi.ethnowear.util.TextUtils.isBlank;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MediaAssetService {

    private final MediaAssetRepository mediaAssetRepository;
    private final SourceReferenceRepository sourceReferenceRepository;
    private final MediaAssetMapper mediaAssetMapper;
    private final MediaAssetUsageChecker usageChecker;
    private final MediaPathResolver paths;

    public Page<MediaAssetDetails> findAll(Pageable pageable) {
        return mediaAssetRepository.findAll(pageable)
                .map(mediaAssetMapper::toDetails);
    }

    public MediaAssetDetails findById(Long id) {
        return mediaAssetMapper.toDetails(requireAsset(id));
    }

    @Transactional
    public MediaAssetDetails updateMetadata(Long id, MediaAssetMetadataWriteDto input) {
        if(input == null)
            throw new IllegalArgumentException("Media asset metadata is required");

        MediaAsset asset = requireAsset(id);
        SourceReference sourceReference = requireSourceReference(input.sourceReferenceId());
        mediaAssetMapper.applyMetadata(asset, input, sourceReference);

        return mediaAssetMapper.toDetails(mediaAssetRepository.save(asset));
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

    private void deleteManagedFile(String relativePath) {
        if (isBlank(relativePath)) return;
        try {
            Files.deleteIfExists(paths.resolveExisting(relativePath));
        } catch (IOException ex) {
            throw new IllegalStateException("Could not delete managed media file", ex);
        }
    }

}
