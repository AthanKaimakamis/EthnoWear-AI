package fmi.ethnowear.application.service.archive.media;

import fmi.ethnowear.persistence.jpa.repository.ArchiveItemMediaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MediaAssetUsageChecker {
    private final ArchiveItemMediaRepository repository;

    public boolean isInUse(Long mediaAssetId) {
        return repository.existsByMediaAsset_Id(mediaAssetId);
    }
}
