package fmi.ethnowear.application.service.document.query.mapper;

import fmi.ethnowear.application.dto.document.query.DocumentPageDetails;
import fmi.ethnowear.application.dto.document.query.DocumentPageMediaDetails;
import fmi.ethnowear.application.dto.document.query.DocumentPageSummaryDetails;
import fmi.ethnowear.persistence.jpa.entity.AppendOnlyEntity;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPage;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPageMedia;
import fmi.ethnowear.persistence.jpa.projection.document.DocumentPagePreviewMediaProjection;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static fmi.ethnowear.domain.model.document.DocumentPageRenditionType.THUMBNAIL;
import static fmi.ethnowear.util.TextUtils.isBlank;

@Component
public class DocumentPageQueryMapper {

    public DocumentPageSummaryDetails toSummary(
            @NonNull DocumentPage page,
            Long previewMediaAssetId
    ) {
        return new DocumentPageSummaryDetails(
                page.getId(),
                id(page.getDocument()),
                id(page.getSourceReference()),
                page.getPageKind(),
                page.getPageRole(),
                page.getPageSequence(),
                page.getPdfPageIndex(),
                page.getPrintedPageNumber(),
                page.getPrintedPageSort(),
                page.getPageLabel(),
                page.getProvenanceStatus(),
                page.getProcessingState(),
                page.getReviewState(),
                page.getTranscriptionApprovalState(),
                page.getProvenanceTrustState(),
                page.getIndexingState(),
                page.getEvidenceState(),
                page.getOcrConfidence(),
                !isBlank(page.getRawOcrText()),
                !isBlank(page.getCorrectedText()),
                previewMediaAssetId,
                page.getCreatedAt(),
                page.getUpdatedAt()
        );
    }

    public DocumentPageDetails toDetails(
            DocumentPage page,
            @NonNull List<DocumentPageMedia> media
    ) {
        Long previewMediaAssetId = media.stream()
                .min(mediaComparator())
                .map(DocumentPageMedia::getMediaAsset)
                .map(AppendOnlyEntity::getId)
                .orElse(null);

        return new DocumentPageDetails(
                toSummary(page, previewMediaAssetId),
                page.getRawOcrText(),
                page.getCorrectedText(),
                page.getOcrEngine(),
                page.getOcrEngineVersion(),
                page.getOcrLanguage(),
                page.getReviewer(),
                page.getReviewedAt(),
                page.getReviewNotes(),
                id(page.getCanonicalDocumentPage()),
                page.getProvenanceNote(),
                page.getProvenanceReviewedBy(),
                page.getProvenanceReviewedAt(),
                media.stream()
                        .map(this::toDetails)
                        .toList()
        );
    }

    public DocumentPageMediaDetails toDetails(
            @NonNull DocumentPageMedia pageMedia
    ) {
        return new DocumentPageMediaDetails(
                pageMedia.getId(),
                id(pageMedia.getMediaAsset()),
                pageMedia.getMediaAsset().getFileName(),
                pageMedia.getMediaAsset().getMimeType(),
                pageMedia.getRenditionType(),
                pageMedia.isOriginal(),
                pageMedia.isPreferredOcrInput(),
                id(pageMedia.getDerivativeOf()),
                id(pageMedia.getProducingJob()),
                pageMedia.getDisplayOrder(),
                pageMedia.getWidth(),
                pageMedia.getHeight(),
                pageMedia.getDpi(),
                pageMedia.getColorMode(),
                pageMedia.getNotes(),
                pageMedia.getCreatedAt(),
                pageMedia.getUpdatedAt()
        );
    }

    public Map<Long, Long> toPreviewMediaIds(
            @NonNull Collection<DocumentPagePreviewMediaProjection> candidates
    ) {
        Map<Long, DocumentPagePreviewMediaProjection> selected =
                new LinkedHashMap<>();

        candidates.forEach(candidate -> selected.merge(
                candidate.getDocumentPageId(),
                candidate,
                this::preferred
        ));

        Map<Long, Long> result = new LinkedHashMap<>();

        selected.forEach((documentPageId, candidate) -> result.put(
                documentPageId,
                candidate.getMediaAssetId()
        ));

        return Collections.unmodifiableMap(result);
    }

    private DocumentPagePreviewMediaProjection preferred(
            DocumentPagePreviewMediaProjection left,
            DocumentPagePreviewMediaProjection right
    ) {
        return previewComparator().compare(left, right) <= 0
                ? left
                : right;
    }

    private @NonNull Comparator<DocumentPagePreviewMediaProjection>
    previewComparator() {
        return Comparator
                .comparingInt(
                        (DocumentPagePreviewMediaProjection candidate) ->
                                priority(
                                        candidate.getRenditionType()
                                                == THUMBNAIL,
                                        candidate.isPreferredOcrInput()
                                )
                )
                .thenComparing(
                        DocumentPagePreviewMediaProjection::getDisplayOrder
                )
                .thenComparing(
                        DocumentPagePreviewMediaProjection::getMediaAssetId
                );
    }

    private @NonNull Comparator<DocumentPageMedia> mediaComparator() {
        return Comparator
                .comparingInt(
                        (DocumentPageMedia pageMedia) -> priority(
                                pageMedia.getRenditionType() == THUMBNAIL,
                                pageMedia.isPreferredOcrInput()
                        )
                )
                .thenComparing(DocumentPageMedia::getDisplayOrder)
                .thenComparing(DocumentPageMedia::getId);
    }

    private int priority(
            boolean thumbnail,
            boolean preferredOcrInput
    ) {
        if (thumbnail)
            return 0;

        if (preferredOcrInput)
            return 1;

        return 2;
    }

    private Long id(AppendOnlyEntity entity) {
        return entity == null ? null : entity.getId();
    }
}