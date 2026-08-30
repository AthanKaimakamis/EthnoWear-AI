package fmi.ethnowear.application.service.document.query.mapper;

import fmi.ethnowear.application.dto.document.query.DocumentIndexingStatusDetails;
import fmi.ethnowear.domain.model.document.indexing.IndexingState;
import fmi.ethnowear.persistence.jpa.entity.document.Document;
import fmi.ethnowear.persistence.jpa.projection.document.DocumentIndexingStateCountProjection;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class DocumentIndexingStatusMapper {

    public Map<Long, DocumentIndexingStatusDetails> toDetails(
            @NonNull Collection<Document> documents,
            @NonNull Collection<DocumentIndexingStateCountProjection> chunkCounts
    ) {
        Map<Long, Map<IndexingState, Long>> countsByDocument =
                new LinkedHashMap<>();

        documents.forEach(document -> countsByDocument.put(
                document.getId(),
                zeroCounts()
        ));

        chunkCounts.forEach(projection -> {
            Map<IndexingState, Long> counts = countsByDocument.get(
                    projection.getDocumentId()
            );

            if (counts != null)
                counts.put(
                        projection.getState(),
                        projection.getTotal()
                );
        });

        Map<Long, DocumentIndexingStatusDetails> result =
                new LinkedHashMap<>();

        documents.forEach(document -> {
            Map<IndexingState, Long> counts = countsByDocument.get(
                    document.getId()
            );

            result.put(
                    document.getId(),
                    new DocumentIndexingStatusDetails(
                            document.getId(),
                            document.getIndexingState(),
                            total(counts),
                            counts
                    )
            );
        });

        return Collections.unmodifiableMap(result);
    }

    private @NonNull Map<IndexingState, Long> zeroCounts() {
        Map<IndexingState, Long> counts =
                new EnumMap<>(IndexingState.class);

        for (IndexingState state : IndexingState.values())
            counts.put(state, 0L);

        return counts;
    }

    private long total(@NonNull Map<IndexingState, Long> counts) {
        return counts.values()
                .stream()
                .mapToLong(Long::longValue)
                .sum();
    }
}
