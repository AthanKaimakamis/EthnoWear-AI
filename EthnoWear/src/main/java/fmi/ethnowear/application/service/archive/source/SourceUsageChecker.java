package fmi.ethnowear.application.service.archive.source;

import fmi.ethnowear.persistence.jpa.repository.SourceReferenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SourceUsageChecker {

    private final SourceReferenceRepository sourceReferenceRepository;

    public boolean isInUse(Long sourceId) {
        return sourceReferenceRepository.existsBySource_Id(sourceId);
    }
}
