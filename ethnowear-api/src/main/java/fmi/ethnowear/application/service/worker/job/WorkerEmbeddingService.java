package fmi.ethnowear.application.service.worker.job;

import fmi.ethnowear.application.dto.worker.indexing.WorkerEmbeddingDetails;
import fmi.ethnowear.application.dto.worker.indexing.WorkerIndexingContextDetails;
import fmi.ethnowear.application.exception.RetrievalUnavailableException;
import fmi.ethnowear.application.exception.WorkerManifestConflictException;
import fmi.ethnowear.application.model.worker.WorkerClaimCredentials;
import fmi.ethnowear.application.port.retrieval.QueryEmbedding;
import fmi.ethnowear.application.port.retrieval.QueryEmbeddingGateway;
import fmi.ethnowear.config.WorkerIndexingProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class WorkerEmbeddingService {

    private final WorkerIndexingService indexingService;
    private final QueryEmbeddingGateway embeddingGateway;
    private final WorkerIndexingProperties properties;

    public WorkerEmbeddingDetails generate(
            Long jobId,
            WorkerClaimCredentials credentials
    ) {
        WorkerIndexingContextDetails before = indexingService.context(
                jobId,
                credentials
        );
        QueryEmbedding embedding = embeddingGateway.embed(before.content());

        validateEmbedding(embedding);

        WorkerIndexingContextDetails after = indexingService.context(
                jobId,
                credentials
        );

        if (before.knowledgeChunkId() != after.knowledgeChunkId()
                || !Objects.equals(before.contentHash(), after.contentHash())
                || !Objects.equals(before.content(), after.content()))
            throw new WorkerManifestConflictException(
                    "Knowledge chunk changed while generating its embedding"
            );

        return new WorkerEmbeddingDetails(
                after.jobId(),
                after.knowledgeChunkId(),
                after.contentHash(),
                embedding.model(),
                embedding.dimensions(),
                embedding.values()
        );
    }

    private void validateEmbedding(QueryEmbedding embedding) {
        if (embedding == null
                || embedding.values() == null
                || !properties.expectedEmbeddingModel()
                .equals(embedding.model())
                || embedding.dimensions()
                != properties.expectedEmbeddingDimensions()
                || embedding.values().size() != embedding.dimensions()
                || embedding.values().stream()
                .anyMatch(value -> value == null || !Float.isFinite(value)))
            throw new RetrievalUnavailableException(
                    "The generated embedding is invalid"
            );
    }
}
