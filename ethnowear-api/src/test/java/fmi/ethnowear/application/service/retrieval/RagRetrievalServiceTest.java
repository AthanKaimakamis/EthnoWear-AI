package fmi.ethnowear.application.service.retrieval;

import fmi.ethnowear.application.dto.retrieval.GroundedPassageDetails;
import fmi.ethnowear.application.dto.retrieval.GroundedRetrievalDetails;
import fmi.ethnowear.application.dto.retrieval.GroundedRetrievalQuery;
import fmi.ethnowear.application.exception.RetrievalUnavailableException;
import fmi.ethnowear.application.port.retrieval.QueryEmbedding;
import fmi.ethnowear.application.port.retrieval.QueryEmbeddingGateway;
import fmi.ethnowear.application.port.retrieval.VectorSearchCandidate;
import fmi.ethnowear.application.port.retrieval.VectorSearchGateway;
import fmi.ethnowear.config.RetrievalProperties;
import fmi.ethnowear.config.WorkerIndexingProperties;
import fmi.ethnowear.domain.model.archive.KnowledgeChunkType;
import fmi.ethnowear.persistence.jpa.entity.KnowledgeChunk;
import fmi.ethnowear.persistence.jpa.entity.document.KnowledgeChunkPage;
import fmi.ethnowear.persistence.jpa.repository.KnowledgeChunkRepository;
import fmi.ethnowear.persistence.jpa.repository.document.KnowledgeChunkPageRepository;
import fmi.ethnowear.testutil.EntityTestUtils;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class RagRetrievalServiceTest {

    private static final int DIMENSIONS = 1_024;

    private final QueryEmbeddingGateway embeddingGateway = mock(QueryEmbeddingGateway.class);
    private final VectorSearchGateway vectorSearchGateway = mock(VectorSearchGateway.class);
    private final KnowledgeChunkRepository chunkRepository = mock(KnowledgeChunkRepository.class);
    private final KnowledgeChunkPageRepository pageRepository = mock(KnowledgeChunkPageRepository.class);
    private final RetrievalCandidateValidator validator = mock(RetrievalCandidateValidator.class);
    private final GroundedRetrievalMapper mapper = mock(GroundedRetrievalMapper.class);

    private final RagRetrievalService service = new RagRetrievalService(
            new RetrievalProperties(
                    true,
                    5,
                    10,
                    3,
                    30,
                    1_000,
                    Duration.ofSeconds(15),
                    Duration.ofSeconds(10)
            ),
            new WorkerIndexingProperties(
                    100_000,
                    4_096,
                    100,
                    150,
                    255,
                    "bge-m3",
                    DIMENSIONS,
                    "ethnowear_chunks_bge_m3_v1"
            ),
            embeddingGateway,
            vectorSearchGateway,
            chunkRepository,
            pageRepository,
            validator,
            mapper
    );

    @Test
    void retrievesExtraCandidatesAndReturnsOnlySqlEligiblePassages() {
        QueryEmbedding embedding = embedding("bge-m3", DIMENSIONS);
        VectorSearchCandidate eligible = new VectorSearchCandidate(101L, 0.91, "hash");
        VectorSearchCandidate rejected = new VectorSearchCandidate(102L, 0.83, "hash");
        KnowledgeChunk chunk = chunk(101L);
        KnowledgeChunkPage page = page(chunk);
        GroundedPassageDetails passage = passage(101L, 0.91);

        when(embeddingGateway.embed("Какво е шопска шевица?")).thenReturn(embedding);
        when(vectorSearchGateway.search(embedding, 15)).thenReturn(List.of(rejected, eligible));
        when(chunkRepository.findRetrievalCandidatesByIdIn(List.of(101L, 102L)))
                .thenReturn(List.of(chunk));
        when(pageRepository.findRetrievalPagesByChunkIdIn(List.of(101L, 102L)))
                .thenReturn(List.of(page));
        when(validator.isEligible(chunk, eligible, List.of(page))).thenReturn(true);
        when(mapper.toDetails(chunk, eligible, List.of(page))).thenReturn(passage);

        GroundedRetrievalDetails result = service.retrieve(
                new GroundedRetrievalQuery("  Какво е шопска шевица?  ", 5)
        );

        assertEquals(1, result.resultCount());
        assertEquals(List.of(passage), result.passages());
        verify(vectorSearchGateway).search(embedding, 15);
    }

    @Test
    void rejectsEmbeddingThatDoesNotMatchIndexingContract() {
        when(embeddingGateway.embed("въпрос"))
                .thenReturn(embedding("different-model", DIMENSIONS));

        assertThrows(
                RetrievalUnavailableException.class,
                () -> service.retrieve(new GroundedRetrievalQuery("въпрос", 5))
        );

        verifyNoInteractions(vectorSearchGateway, chunkRepository, pageRepository);
    }

    private QueryEmbedding embedding(String model, int dimensions) {
        return new QueryEmbedding(
                Collections.nCopies(dimensions, 0.1F),
                model,
                dimensions
        );
    }

    private KnowledgeChunk chunk(Long id) {
        KnowledgeChunk chunk = new KnowledgeChunk();
        EntityTestUtils.setId(chunk, id);
        return chunk;
    }

    private KnowledgeChunkPage page(KnowledgeChunk chunk) {
        KnowledgeChunkPage page = new KnowledgeChunkPage();
        page.setKnowledgeChunk(chunk);
        return page;
    }

    private GroundedPassageDetails passage(Long chunkId, double similarity) {
        return new GroundedPassageDetails(
                chunkId,
                "Одобрен откъс",
                "bg",
                KnowledgeChunkType.BOOK_EXCERPT,
                1L,
                "Източник",
                List.of(),
                similarity,
                null,
                null,
                null,
                false
        );
    }
}
