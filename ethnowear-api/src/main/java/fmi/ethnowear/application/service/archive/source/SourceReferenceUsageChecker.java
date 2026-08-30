package fmi.ethnowear.application.service.archive.source;

import fmi.ethnowear.persistence.jpa.repository.ArchiveItemFeatureRepository;
import fmi.ethnowear.persistence.jpa.repository.ArchiveItemRepository;
import fmi.ethnowear.persistence.jpa.repository.KnowledgeChunkRepository;
import fmi.ethnowear.persistence.jpa.repository.MediaAssetRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageFigureRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SourceReferenceUsageChecker {

    private final ArchiveItemRepository archiveItemRepository;
    private final ArchiveItemFeatureRepository featureRepository;
    private final MediaAssetRepository mediaAssetRepository;
    private final KnowledgeChunkRepository knowledgeChunkRepository;
    private final DocumentPageFigureRepository documentPageFigureRepository;

    public boolean isInUse(Long sourceReferenceId) {
        return archiveItemRepository.existsBySourceReference_Id(sourceReferenceId)
                || featureRepository.existsBySourceReference_Id(sourceReferenceId)
                || mediaAssetRepository.existsBySourceReference_Id(sourceReferenceId)
                || knowledgeChunkRepository.existsBySourceReference_Id(sourceReferenceId)
                || documentPageFigureRepository.existsBySourceReference_Id(sourceReferenceId);
    }
}
