package fmi.ethnowear.application.service.archive.item;

import fmi.ethnowear.persistence.jpa.repository.ArchiveItemFeatureRepository;
import fmi.ethnowear.persistence.jpa.repository.ArchiveItemMediaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ArchiveItemUsageChecker {

    private final ArchiveItemFeatureRepository featureRepository;
    private final ArchiveItemMediaRepository mediaRepository;

    public boolean isInUse(Long archiveItemId) {
        return featureRepository.existsByArchiveItem_Id(archiveItemId)
                || mediaRepository.existsByArchiveItem_Id(archiveItemId);
    }
}
