package fmi.ethnowear.application.service.archive.media.asset;

import fmi.ethnowear.persistence.jpa.repository.ArchiveItemMediaRepository;
import fmi.ethnowear.persistence.jpa.repository.MediaEntityLinkRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class MediaAssetUsageChecker {
    private final ArchiveItemMediaRepository repository;
    private final MediaEntityLinkRepository entityLinkRepository;

    @Autowired
    public MediaAssetUsageChecker(ArchiveItemMediaRepository repository, MediaEntityLinkRepository entityLinkRepository) {
        this.repository = repository;
        this.entityLinkRepository = entityLinkRepository;
    }

    public MediaAssetUsageChecker(ArchiveItemMediaRepository repository) {
        this(repository, null);
    }

    public boolean isInUse(Long mediaAssetId) {
        return repository.existsByMediaAsset_Id(mediaAssetId)
                || entityLinkRepository != null && entityLinkRepository.existsByMediaAsset_Id(mediaAssetId);
    }
}
