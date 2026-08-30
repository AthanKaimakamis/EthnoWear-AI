package fmi.ethnowear.application.port.retrieval;

import java.util.List;

public interface VectorSearchGateway {

    List<VectorSearchCandidate> search(
            QueryEmbedding embedding,
            int candidateCount
    );
}