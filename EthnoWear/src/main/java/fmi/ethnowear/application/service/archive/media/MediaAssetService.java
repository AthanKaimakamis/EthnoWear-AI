package fmi.ethnowear.application.service.archive.media;

import fmi.ethnowear.api.dto.archive.media.MediaAssetDetails;
import fmi.ethnowear.api.dto.archive.media.MediaAssetWriteDto;
import fmi.ethnowear.application.exceptions.ResourceInUseException;
import fmi.ethnowear.application.exceptions.ResourceNotFoundException;
import fmi.ethnowear.application.service.ICrudService;
import fmi.ethnowear.dal.entity.MediaAsset;
import fmi.ethnowear.dal.entity.SourceReference;
import fmi.ethnowear.dal.repository.MediaAssetRepository;
import fmi.ethnowear.dal.repository.SourceReferenceRepository;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static fmi.ethnowear.util.TextUtils.isBlank;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MediaAssetService implements ICrudService<MediaAssetWriteDto, MediaAssetDetails> {

    private final MediaAssetRepository mediaAssetRepository;
    private final SourceReferenceRepository sourceReferenceRepository;
    private final MediaAssetMapper mediaAssetMapper;
    private final MediaAssetUsageChecker usageChecker;

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

        if (isBlank(input.filePath()) && isBlank(input.storageUrl()))
            throw new IllegalArgumentException("File path or storage URL is required");

        if (input.width() != null && input.width() <= 0)
            throw new IllegalArgumentException("Media width must be positive");

        if (input.height() != null && input.height() <= 0)
            throw new IllegalArgumentException("Media height must be positive");

        if (input.sizeBytes() != null && input.sizeBytes() < 0)
            throw new IllegalArgumentException("Media size cannot be negative");
    }

}
