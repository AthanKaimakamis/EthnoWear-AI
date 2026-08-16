package fmi.ethnowear.application.service.archive.media;

import fmi.ethnowear.application.dto.archive.media.MediaAssetDetails;
import fmi.ethnowear.application.dto.archive.media.MediaAssetWriteDto;
import fmi.ethnowear.application.exception.ResourceInUseException;
import fmi.ethnowear.application.exception.ResourceNotFoundException;
import fmi.ethnowear.application.service.CrudService;
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

import static fmi.ethnowear.util.TextUtils.isBlank;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MediaAssetService implements CrudService<MediaAssetWriteDto, MediaAssetDetails> {

    private final MediaAssetRepository mediaAssetRepository;
    private final SourceReferenceRepository sourceReferenceRepository;
    private final MediaAssetMapper mediaAssetMapper;
    private final MediaAssetUsageChecker usageChecker;
    private final MediaPathResolver paths;

    @Override
    public Page<MediaAssetDetails> findAll(Pageable pageable) {
        return mediaAssetRepository.findAll(pageable)
                .map(mediaAssetMapper::toDetails);
    }

    @Override
    public MediaAssetDetails findById(Long id) {
        return mediaAssetMapper.toDetails(requireAsset(id));
    }

    @Override
    @Transactional
    public MediaAssetDetails create(MediaAssetWriteDto input) {
        validate(input);

        MediaAsset asset = new MediaAsset();
        apply(asset, input);

        return mediaAssetMapper.toDetails(mediaAssetRepository.save(asset));
    }

    @Override
    @Transactional
    public MediaAssetDetails update(Long id, MediaAssetWriteDto input) {
        validate(input);

        MediaAsset asset = requireAsset(id);
        apply(asset, input);

        return mediaAssetMapper.toDetails(mediaAssetRepository.save(asset));
    }

    @Override
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

    private void deleteManagedFile(String relativePath) {
        if (isBlank(relativePath)) return;
        try {
            Files.deleteIfExists(paths.resolve(relativePath));
        } catch (IOException ex) {
            throw new IllegalStateException("Could not delete managed media file", ex);
        }
    }

    private void apply(MediaAsset asset, @NonNull MediaAssetWriteDto input) {
        SourceReference reference = input.sourceReferenceId() == null
                ? null
                : sourceReferenceRepository.findById(input.sourceReferenceId())
                    .orElseThrow(() -> new ResourceNotFoundException("Source reference", input.sourceReferenceId()));

        mediaAssetMapper.apply(asset, input, reference);
    }

    private @NonNull MediaAsset requireAsset(Long id) {
        return mediaAssetRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Media asset", id));
    }

    private void validate(MediaAssetWriteDto input) {
        if (input == null)
            throw new IllegalArgumentException("Media asset input is required");

        if (input.mediaType() == null)
            throw new IllegalArgumentException("Media type is required");

        if (isBlank(input.filePath()))
            throw new IllegalArgumentException("A relative managed file path is required");

        if (!isBlank(input.storageUrl()))
            throw new IllegalArgumentException("External storage URLs are not supported");

        paths.resolve(input.filePath());

        if (input.width() != null && input.width() <= 0)
            throw new IllegalArgumentException("Media width must be positive");

        if (input.height() != null && input.height() <= 0)
            throw new IllegalArgumentException("Media height must be positive");

        if (input.sizeBytes() != null && input.sizeBytes() < 0)
            throw new IllegalArgumentException("Media size cannot be negative");
    }

}
