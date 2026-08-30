package fmi.ethnowear.application.service.worker.job;

import fmi.ethnowear.application.dto.worker.indexing.WorkerEmbeddingDetails;
import fmi.ethnowear.application.dto.worker.indexing.WorkerIndexingContextDetails;
import fmi.ethnowear.application.model.worker.WorkerClaimCredentials;
import fmi.ethnowear.application.port.retrieval.QueryEmbedding;
import fmi.ethnowear.application.port.retrieval.QueryEmbeddingGateway;
import fmi.ethnowear.config.WorkerIndexingProperties;
import fmi.ethnowear.domain.model.archive.KnowledgeChunkType;
import fmi.ethnowear.domain.model.document.provenance.ProvenanceTrustState;
import fmi.ethnowear.domain.model.document.review.TranscriptionApprovalState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkerEmbeddingServiceTest {

    @Test
    void generatesEmbeddingFromClaimedAuthoritativeContent() {
        WorkerIndexingService indexingService = mock(WorkerIndexingService.class);
        QueryEmbeddingGateway gateway = mock(QueryEmbeddingGateway.class);
        WorkerClaimCredentials credentials = new WorkerClaimCredentials(
                "indexer-1",
                "claim-token"
        );
        WorkerIndexingContextDetails context = context();

        when(indexingService.context(11L, credentials))
                .thenReturn(context);
        when(gateway.embed(context.content()))
                .thenReturn(new QueryEmbedding(
                        List.of(0.1f, 0.2f, 0.3f),
                        "bge-m3",
                        3
                ));

        WorkerEmbeddingService service = new WorkerEmbeddingService(
                indexingService,
                gateway,
                properties()
        );

        WorkerEmbeddingDetails result = service.generate(11L, credentials);

        assertEquals(22L, result.knowledgeChunkId());
        assertEquals("content-hash", result.contentHash());
        assertEquals(List.of(0.1f, 0.2f, 0.3f), result.values());
        verify(gateway).embed(context.content());
    }

    private WorkerIndexingContextDetails context() {
        return new WorkerIndexingContextDetails(
                11L,
                22L,
                "Одобрен текст",
                "content-hash",
                "bg",
                KnowledgeChunkType.BOOK_EXCERPT,
                33L,
                44L,
                null,
                null,
                ProvenanceTrustState.VERIFIED,
                TranscriptionApprovalState.APPROVED
        );
    }

    private WorkerIndexingProperties properties() {
        return new WorkerIndexingProperties(
                100_000,
                4_096,
                100,
                150,
                255,
                "bge-m3",
                3,
                "chunks"
        );
    }
}
