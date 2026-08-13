package fmi.ethnowear.application.service.archive.source;

import fmi.ethnowear.dal.repository.ArchiveItemFeatureRepository;
import fmi.ethnowear.dal.repository.ArchiveItemRepository;
import fmi.ethnowear.dal.repository.KnowledgeChunkRepository;
import fmi.ethnowear.dal.repository.MediaAssetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SourceReferenceUsageChecker {

    private final ArchiveItemRepository archiveItemRepository;
    private final ArchiveItemFeatureRepository featureRepository;
    private final MediaAssetRepository mediaAssetRepository;
    private final KnowledgeChunkRepository knowledgeChunkRepository;

    public boolean isInUse(Long sourceReferenceId) {
        return archiveItemRepository.existsBySourceReference_Id(sourceReferenceId)
                || featureRepository.existsBySourceReference_Id(sourceReferenceId)
                || mediaAssetRepository.existsBySourceReference_Id(sourceReferenceId)
                || knowledgeChunkRepository.existsBySourceReference_Id(sourceReferenceId);
    }
}
