package fmi.ethnowear.application.service.archive.media.attachment;

import fmi.ethnowear.persistence.jpa.repository.MediaFeatureAnnotationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ArchiveItemMediaUsageChecker {

    private final MediaFeatureAnnotationRepository repository;

    public boolean isInUse(Long archiveItemMediaId) {
        return repository.existsByArchiveItemMedia_Id(archiveItemMediaId);
    }
}
