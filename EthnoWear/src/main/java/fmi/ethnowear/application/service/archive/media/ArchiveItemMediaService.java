package fmi.ethnowear.application.service.archive.media;

import fmi.ethnowear.application.dto.archive.media.ArchiveItemMediaDetails;
import fmi.ethnowear.application.dto.archive.media.ArchiveItemMediaWriteDto;
import fmi.ethnowear.application.exception.ResourceInUseException;
import fmi.ethnowear.application.exception.ResourceNotFoundException;
import fmi.ethnowear.application.service.CrudService;
import fmi.ethnowear.application.service.archive.workflow.ArchiveItemWorkflowGuard;
import fmi.ethnowear.persistence.jpa.entity.ArchiveItem;
import fmi.ethnowear.persistence.jpa.entity.ArchiveItemMedia;
import fmi.ethnowear.persistence.jpa.entity.MediaAsset;
import fmi.ethnowear.persistence.jpa.repository.ArchiveItemMediaRepository;
import fmi.ethnowear.persistence.jpa.repository.ArchiveItemRepository;
import fmi.ethnowear.persistence.jpa.repository.MediaAssetRepository;
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
    private final ArchiveItemWorkflowGuard workflowGuard;

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
        workflowGuard.requireDraft(itemMedia.getArchiveItem());
        apply(itemMedia, input);

        return archiveItemMediaMapper.toDetails(archiveItemMediaRepository.save(itemMedia));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        ArchiveItemMedia itemMedia = requireItemMedia(id);
        workflowGuard.requireDraft(itemMedia.getArchiveItem());

        if (usageChecker.isInUse(id))
            throw new ResourceInUseException("Archive item media", id);

        archiveItemMediaRepository.delete(itemMedia);
    }

    private void apply(@NonNull ArchiveItemMedia itemMedia, @NonNull ArchiveItemMediaWriteDto input) {
        ArchiveItem archiveItem = archiveItemRepository.findById(input.archiveItemId())
                .orElseThrow(() -> new ResourceNotFoundException("Archive item", input.archiveItemId()));

        workflowGuard.requireDraft(archiveItem);

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
