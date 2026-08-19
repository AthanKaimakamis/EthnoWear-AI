package fmi.ethnowear.application.service.document.query.mapper;

import fmi.ethnowear.domain.model.document.indexing.IndexingState;
import fmi.ethnowear.persistence.jpa.entity.document.Document;
import fmi.ethnowear.persistence.jpa.projection.document.DocumentIndexingStateCountProjection;
import fmi.ethnowear.testutil.EntityTestUtils;
import org.junit.jupiter.api.Test;

import java.util.List;

import static fmi.ethnowear.support.RepositoryTestProxies.proxy;
import static org.junit.jupiter.api.Assertions.assertEquals;

class DocumentIndexingStatusMapperTest {

    @Test
    void mapsDocumentStateAndInitializesMissingChunkStates() {
        Document document = new Document();
        EntityTestUtils.setId(document, 7L);
        document.setIndexingState(IndexingState.OUTDATED);

        DocumentIndexingStateCountProjection indexed = proxy(
                DocumentIndexingStateCountProjection.class,
                (ignored, method, arguments) -> switch (method.getName()) {
                    case "getDocumentId" -> 7L;
                    case "getState" -> IndexingState.INDEXED;
                    case "getTotal" -> 3L;
                    default -> throw new AssertionError(method.getName());
                }
        );

        var result = new DocumentIndexingStatusMapper()
                .toDetails(List.of(document), List.of(indexed))
                .get(7L);

        assertEquals(IndexingState.OUTDATED, result.documentState());
        assertEquals(3L, result.totalChunks());
        assertEquals(3L, result.chunkCounts().get(IndexingState.INDEXED));
        assertEquals(0L, result.chunkCounts().get(IndexingState.FAILED));
    }
}
