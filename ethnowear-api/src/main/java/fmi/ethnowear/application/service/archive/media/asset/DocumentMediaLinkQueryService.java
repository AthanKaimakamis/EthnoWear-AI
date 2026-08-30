package fmi.ethnowear.application.service.archive.media.asset;

import fmi.ethnowear.application.dto.archive.media.DocumentMediaLinkDetails;
import fmi.ethnowear.persistence.jpa.entity.document.Document;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPage;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPageMedia;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageMediaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DocumentMediaLinkQueryService {

    private static final int MAX_MEDIA_ASSET_IDS = 100;

    private final DocumentPageMediaRepository repository;

    public List<DocumentMediaLinkDetails> findByMediaAssetIds(Collection<Long> mediaAssetIds) {
        if (mediaAssetIds == null || mediaAssetIds.isEmpty()) return List.of();
        if (mediaAssetIds.size() > MAX_MEDIA_ASSET_IDS)
            throw new IllegalArgumentException("Media asset query cannot exceed 100 identifiers");

        return repository.findDocumentLinksByMediaAsset_IdIn(mediaAssetIds).stream()
                .map(this::toDetails)
                .toList();
    }

    private DocumentMediaLinkDetails toDetails(DocumentPageMedia pageMedia) {
        DocumentPage page = pageMedia.getDocumentPage();
        Document document = page.getDocument();
        return new DocumentMediaLinkDetails(
                pageMedia.getId(),
                pageMedia.getMediaAsset().getId(),
                page.getId(),
                document.getId(),
                page.getSourceReference() == null ? null : page.getSourceReference().getId(),
                document.getSource() == null ? null : document.getSource().getId()
        );
    }
}
