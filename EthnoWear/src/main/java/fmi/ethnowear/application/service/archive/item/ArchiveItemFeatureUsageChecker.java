package fmi.ethnowear.application.service.archive.item;

import fmi.ethnowear.dal.repository.MediaFeatureAnnotationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ArchiveItemFeatureUsageChecker {

    private final MediaFeatureAnnotationRepository annotationRepository;

    public boolean isInUse(Long archiveItemFeatureId) {
        return annotationRepository.existsByArchiveItemFeature_Id(archiveItemFeatureId);
    }
}
