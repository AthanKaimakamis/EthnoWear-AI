package fmi.ethnowear.application.service.document.query.mapper;

import fmi.ethnowear.application.dto.document.query.*;
import fmi.ethnowear.application.dto.document.query.history.BoundedHistoryDetails;
import fmi.ethnowear.application.dto.document.query.history.DocumentProcessingJobDetails;
import fmi.ethnowear.persistence.jpa.entity.Source;
import fmi.ethnowear.persistence.jpa.entity.document.Document;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

@Component
public class DocumentQueryMapper {

    private final DocumentProgressMapper progressMapper;

    public DocumentQueryMapper(DocumentProgressMapper progressMapper) {
        this.progressMapper = progressMapper;
    }

    public DocumentSummaryDetails toSummary(Document document, DocumentProgressDetails progress) {
        return toSummary(document, progressMapper.toSummary(progress));
    }

    public DocumentSummaryDetails toSummary(
            @NonNull Document document,
            DocumentProgressSummaryDetails progress
    ) {
        Source source = document.getSource();

        return new DocumentSummaryDetails(
                document.getId(),
                id(source),
                source == null ? null : source.getTitle(),
                document.getDefaultSourceReference() == null
                        ? null
                        : document.getDefaultSourceReference().getId(),
                document.getOriginalMediaAsset() == null
                        ? null
                        : document.getOriginalMediaAsset().getId(),
                document.getThumbnailMediaAsset() == null
                        ? null
                        : document.getThumbnailMediaAsset().getId(),
                document.getDocumentType(),
                document.getProvenanceStatus(),
                document.getTitle(),
                document.getAuthor(),
                document.getPublisher(),
                document.getPublicationYear(),
                document.getLanguage(),
                document.getPageCount(),
                document.getProcessingState(),
                document.getReviewState(),
                document.getProvenanceTrustState(),
                document.getIndexingState(),
                progress,
                document.getCreatedAt(),
                document.getUpdatedAt()
        );
    }

    public DocumentSourceDetails toSource(Source source) {
        if (source == null)
            return null;

        return new DocumentSourceDetails(
                source.getId(),
                source.getTitle(),
                source.getAuthor(),
                source.getPublisher(),
                source.getYear(),
                source.getSourceType(),
                source.getLanguage(),
                source.getUrl(),
                source.getIsbn(),
                source.getNotes(),
                source.isTrusted()
        );
    }

    public DocumentDetails toDetails(
            Document document,
            DocumentProgressDetails progress,
            DocumentIndexingStatusDetails indexingStatus,
            BoundedHistoryDetails<DocumentProcessingJobDetails> recentJobs
    ) {
        return new DocumentDetails(
                toSummary(
                        document,
                        progressMapper.toSummary(progress)
                ),
                toSource(document.getSource()),
                document.getNotes(),
                document.getMergedIntoDocument() == null
                        ? null
                        : document.getMergedIntoDocument().getId(),
                progress,
                indexingStatus,
                recentJobs
        );
    }

    private Long id(Source source) {
        return source == null ? null : source.getId();
    }
}
