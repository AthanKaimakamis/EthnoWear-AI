package fmi.ethnowear.application.service.archive.media;

import fmi.ethnowear.api.dto.archive.media.ArchiveItemMediaDetails;
import fmi.ethnowear.api.dto.archive.media.ArchiveItemMediaWriteDto;
import fmi.ethnowear.application.exceptions.ResourceInUseException;
import fmi.ethnowear.application.exceptions.ResourceNotFoundException;
import fmi.ethnowear.application.service.CrudService;
import fmi.ethnowear.dal.entity.ArchiveItem;
import fmi.ethnowear.dal.entity.ArchiveItemMedia;
import fmi.ethnowear.dal.entity.MediaAsset;
import fmi.ethnowear.dal.repository.ArchiveItemMediaRepository;
import fmi.ethnowear.dal.repository.ArchiveItemRepository;
import fmi.ethnowear.dal.repository.MediaAssetRepository;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ArchiveItemMediaService implements CrudService<ArchiveItemMediaWriteDto, ArchiveItemMediaDetails> {

    private final ArchiveItemMediaRepository archiveItemMediaRepository;
    private final ArchiveItemRepository archiveItemRepository;
    private final MediaAssetRepository mediaAssetRepository;
    private final ArchiveItemMediaMapper archiveItemMediaMapper;
    private final ArchiveItemMediaUsageChecker usageChecker;

    @Override
    public Page<ArchiveItemMediaDetails> findAll(Pageable pageable) {
        return archiveItemMediaRepository.findAll(pageable)
                .map(archiveItemMediaMapper::toDetails);
    }

    @Override
    public ArchiveItemMediaDetails findById(Long id) {
        return archiveItemMediaMapper.toDetails(requireItemMedia(id));
    }

    @Override
    @Transactional
    public ArchiveItemMediaDetails create(ArchiveItemMediaWriteDto input) {
        validate(input);

        ArchiveItemMedia itemMedia = new ArchiveItemMedia();
        apply(itemMedia, input);

        return archiveItemMediaMapper.toDetails(archiveItemMediaRepository.save(itemMedia));
    }

    @Override
    @Transactional
    public ArchiveItemMediaDetails update(Long id, ArchiveItemMediaWriteDto input) {
        validate(input);

        ArchiveItemMedia itemMedia = requireItemMedia(id);
        apply(itemMedia, input);

        return archiveItemMediaMapper.toDetails(archiveItemMediaRepository.save(itemMedia));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        ArchiveItemMedia itemMedia = requireItemMedia(id);

        if (usageChecker.isInUse(id))
            throw new ResourceInUseException("Archive item media", id);

        archiveItemMediaRepository.delete(itemMedia);
    }

    private void apply(ArchiveItemMedia itemMedia, @NonNull ArchiveItemMediaWriteDto input) {
        ArchiveItem archiveItem = archiveItemRepository.findById(input.archiveItemId())
                .orElseThrow(() -> new ResourceNotFoundException("Archive item", input.archiveItemId()));

        MediaAsset mediaAsset = mediaAssetRepository.findById(input.mediaAssetId())
                .orElseThrow(() -> new ResourceNotFoundException("Media asset", input.mediaAssetId()));

        archiveItemMediaMapper.apply(itemMedia, input, archiveItem, mediaAsset);
    }

    private @NonNull ArchiveItemMedia requireItemMedia(Long id) {
        return archiveItemMediaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Archive item media", id));
    }

    private void validate(ArchiveItemMediaWriteDto input) {
        if (input == null)
            throw new IllegalArgumentException("Archive item media input is required");

        if (input.archiveItemId() == null)
            throw new IllegalArgumentException("Archive item is required");

        if (input.mediaAssetId() == null)
            throw new IllegalArgumentException("Media asset is required");

        if (input.role() == null)
            throw new IllegalArgumentException("Media role is required");
    }
}
