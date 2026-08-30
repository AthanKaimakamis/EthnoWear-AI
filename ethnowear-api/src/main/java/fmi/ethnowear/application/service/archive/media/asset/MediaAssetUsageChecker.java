package fmi.ethnowear.application.service.archive.media.asset;

import fmi.ethnowear.persistence.jpa.repository.ArchiveItemMediaRepository;
import fmi.ethnowear.persistence.jpa.repository.MediaEntityLinkRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageMediaRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageFigureRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class MediaAssetUsageChecker {
    private final ArchiveItemMediaRepository repository;
    private final MediaEntityLinkRepository entityLinkRepository;
    private final DocumentRepository documentRepository;
    private final DocumentPageMediaRepository documentPageMediaRepository;
    private final DocumentPageFigureRepository documentPageFigureRepository;

    @Autowired
    public MediaAssetUsageChecker(ArchiveItemMediaRepository repository, MediaEntityLinkRepository entityLinkRepository, DocumentRepository documentRepository, DocumentPageMediaRepository documentPageMediaRepository, DocumentPageFigureRepository documentPageFigureRepository) {
        this.repository = repository;
        this.entityLinkRepository = entityLinkRepository;
        this.documentRepository = documentRepository;
        this.documentPageMediaRepository = documentPageMediaRepository;
        this.documentPageFigureRepository = documentPageFigureRepository;
    }

    public MediaAssetUsageChecker(ArchiveItemMediaRepository repository) {
        this(repository, null, null, null, null);
    }

    public boolean isInUse(Long mediaAssetId) {
        return repository.existsByMediaAsset_Id(mediaAssetId)
                || entityLinkRepository != null && entityLinkRepository.existsByMediaAsset_Id(mediaAssetId)
                || documentRepository != null && (documentRepository.existsByOriginalMediaAsset_Id(mediaAssetId)
                || documentRepository.existsByThumbnailMediaAsset_Id(mediaAssetId))
                || documentPageMediaRepository != null && documentPageMediaRepository.existsByMediaAsset_Id(mediaAssetId)
                || documentPageFigureRepository != null && documentPageFigureRepository.existsByMediaAsset_Id(mediaAssetId);
    }
}
