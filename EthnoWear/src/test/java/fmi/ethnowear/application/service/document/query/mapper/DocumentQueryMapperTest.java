package fmi.ethnowear.application.service.document.query.mapper;

import fmi.ethnowear.application.dto.document.query.DocumentProgressDetails;
import fmi.ethnowear.domain.model.document.indexing.IndexingState;
import fmi.ethnowear.domain.model.document.processing.ProcessingState;
import fmi.ethnowear.domain.model.document.review.ReviewState;
import fmi.ethnowear.domain.model.document.review.TranscriptionApprovalState;
import fmi.ethnowear.persistence.jpa.entity.MediaAsset;
import fmi.ethnowear.persistence.jpa.entity.Source;
import fmi.ethnowear.persistence.jpa.entity.document.Document;
import fmi.ethnowear.testutil.EntityTestUtils;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DocumentQueryMapperTest {

    private final DocumentQueryMapper mapper = new DocumentQueryMapper(
            new DocumentProgressMapper()
    );

    @Test
    void mapsSafeDocumentAndSourceDetailsUsingMediaIdOnly() {
        Source source = new Source();
        EntityTestUtils.setId(source, 2L);
        source.setTitle("Source title");
        source.setFilePath("documents/internal.pdf");

        MediaAsset media = new MediaAsset();
        EntityTestUtils.setId(media, 3L);
        media.setFilePath("documents/internal.pdf");
        media.setChecksum("internal-checksum");

        Document document = new Document();
        EntityTestUtils.setId(document, 1L);
        document.setSource(source);
        document.setOriginalMediaAsset(media);
        document.setTitle("Document title");

        var result = mapper.toSummary(document, emptyProgress());

        assertEquals(1L, result.id());
        assertEquals(2L, result.sourceId());
        assertEquals("Source title", result.sourceTitle());
        assertEquals(3L, result.originalMediaAssetId());
        assertEquals("Document title", result.title());
    }

    private DocumentProgressDetails emptyProgress() {
        return new DocumentProgressDetails(
                0,
                zeroCounts(ProcessingState.class),
                zeroCounts(ReviewState.class),
                zeroCounts(TranscriptionApprovalState.class),
                zeroCounts(IndexingState.class)
        );
    }

    private <E extends Enum<E>> Map<E, Long> zeroCounts(Class<E> type) {
        Map<E, Long> counts = new EnumMap<>(type);
        for (E value : type.getEnumConstants())
            counts.put(value, 0L);
        return counts;
    }
}
