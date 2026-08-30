package fmi.ethnowear.application.service.document.query.mapper;

import fmi.ethnowear.application.dto.document.query.DocumentProgressDetails;
import fmi.ethnowear.application.dto.document.query.DocumentProgressSummaryDetails;
import fmi.ethnowear.domain.model.document.indexing.IndexingState;
import fmi.ethnowear.domain.model.document.processing.ProcessingState;
import fmi.ethnowear.domain.model.document.review.ReviewState;
import fmi.ethnowear.domain.model.document.review.TranscriptionApprovalState;
import fmi.ethnowear.persistence.jpa.projection.document.DocumentIndexingStateCountProjection;
import fmi.ethnowear.persistence.jpa.projection.document.DocumentProcessingStateCountProjection;
import fmi.ethnowear.persistence.jpa.projection.document.DocumentReviewStateCountProjection;
import fmi.ethnowear.persistence.jpa.projection.document.DocumentTranscriptionApprovalCountProjection;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.ToLongFunction;

@Component
public class DocumentProgressMapper {

    public Map<Long, DocumentProgressDetails> toDetails(
            Collection<Long> documentIds,
            List<DocumentProcessingStateCountProjection> processingCounts,
            List<DocumentReviewStateCountProjection> reviewCounts,
            List<DocumentTranscriptionApprovalCountProjection>
                    transcriptionApprovalCounts,
            List<DocumentIndexingStateCountProjection> indexingCounts
    ) {
        Set<Long> uniqueDocumentIds = new LinkedHashSet<>(documentIds);

        Map<Long, Map<ProcessingState, Long>> processing = groupCounts(
                uniqueDocumentIds,
                ProcessingState.class,
                processingCounts,
                DocumentProcessingStateCountProjection::getDocumentId,
                DocumentProcessingStateCountProjection::getState,
                DocumentProcessingStateCountProjection::getTotal
        );

        Map<Long, Map<ReviewState, Long>> review = groupCounts(
                uniqueDocumentIds,
                ReviewState.class,
                reviewCounts,
                DocumentReviewStateCountProjection::getDocumentId,
                DocumentReviewStateCountProjection::getState,
                DocumentReviewStateCountProjection::getTotal
        );

        Map<Long, Map<TranscriptionApprovalState, Long>>
                transcriptionApproval = groupCounts(
                uniqueDocumentIds,
                TranscriptionApprovalState.class,
                transcriptionApprovalCounts,
                DocumentTranscriptionApprovalCountProjection::getDocumentId,
                DocumentTranscriptionApprovalCountProjection::getState,
                DocumentTranscriptionApprovalCountProjection::getTotal
        );

        Map<Long, Map<IndexingState, Long>> indexing = groupCounts(
                uniqueDocumentIds,
                IndexingState.class,
                indexingCounts,
                DocumentIndexingStateCountProjection::getDocumentId,
                DocumentIndexingStateCountProjection::getState,
                DocumentIndexingStateCountProjection::getTotal
        );

        Map<Long, DocumentProgressDetails> result = new LinkedHashMap<>();

        uniqueDocumentIds.forEach(documentId -> result.put(
                documentId,
                new DocumentProgressDetails(
                        total(processing.get(documentId)),
                        processing.get(documentId),
                        review.get(documentId),
                        transcriptionApproval.get(documentId),
                        indexing.get(documentId)
                )
        ));

        return Collections.unmodifiableMap(result);
    }

    public Map<Long, DocumentProgressSummaryDetails> toSummaries(
            @NonNull Map<Long, DocumentProgressDetails> progressByDocument
    ) {
        Map<Long, DocumentProgressSummaryDetails> result =
                new LinkedHashMap<>();

        progressByDocument.forEach((documentId, progress) -> result.put(
                documentId,
                toSummary(progress)
        ));

        return Collections.unmodifiableMap(result);
    }

    public DocumentProgressSummaryDetails toSummary(
            @NonNull DocumentProgressDetails progress
    ) {
        return new DocumentProgressSummaryDetails(
                progress.totalPages(),
                progress.processing().get(ProcessingState.COMPLETED),
                progress.processing().get(ProcessingState.FAILED),
                progress.review().get(ReviewState.REVIEW_REQUIRED),
                progress.transcriptionApproval().get(
                        TranscriptionApprovalState.APPROVED
                ),
                progress.indexing().get(IndexingState.INDEXED)
        );
    }

    private <P, E extends Enum<E>> @NonNull Map<Long, Map<E, Long>> groupCounts(
            @NonNull Collection<Long> documentIds,
            Class<E> enumType,
            @NonNull Collection<P> projections,
            Function<P, Long> documentIdMapper,
            Function<P, E> stateMapper,
            ToLongFunction<P> totalMapper
    ) {
        Map<Long, Map<E, Long>> grouped = new LinkedHashMap<>();

        documentIds.forEach(documentId -> grouped.put(
                documentId,
                zeroCounts(enumType)
        ));

        projections.forEach(projection -> {
            Map<E, Long> counts = grouped.get(
                    documentIdMapper.apply(projection)
            );

            if (counts != null)
                counts.put(
                        stateMapper.apply(projection),
                        totalMapper.applyAsLong(projection)
                );
        });

        return grouped;
    }

    private <E extends Enum<E>> @NonNull Map<E, Long> zeroCounts(
            Class<E> enumType
    ) {
        Map<E, Long> counts = new EnumMap<>(enumType);

        for (E state : enumType.getEnumConstants())
            counts.put(state, 0L);

        return counts;
    }

    private long total(@NonNull Map<?, Long> counts) {
        return counts.values()
                .stream()
                .mapToLong(Long::longValue)
                .sum();
    }
}