package fmi.ethnowear.application.service.document.query.mapper;

import fmi.ethnowear.domain.model.document.processing.ProcessingState;
import fmi.ethnowear.persistence.jpa.projection.document.DocumentProcessingStateCountProjection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static fmi.ethnowear.support.RepositoryTestProxies.proxy;

class DocumentProgressMapperTest {

    @Test
    void initializesMissingStatesAndAggregatesTotals() {
        DocumentProcessingStateCountProjection completed = proxy(
                DocumentProcessingStateCountProjection.class,
                (ignored, method, arguments) -> switch (method.getName()) {
                    case "getDocumentId" -> 7L;
                    case "getState" -> ProcessingState.COMPLETED;
                    case "getTotal" -> 4L;
                    default -> throw new AssertionError(method.getName());
                }
        );

        var result = new DocumentProgressMapper().toDetails(
                List.of(7L),
                List.of(completed),
                List.of(),
                List.of(),
                List.of()
        ).get(7L);

        assertEquals(4L, result.totalPages());
        assertEquals(4L, result.processing().get(ProcessingState.COMPLETED));
        assertEquals(0L, result.processing().get(ProcessingState.FAILED));
    }
}
