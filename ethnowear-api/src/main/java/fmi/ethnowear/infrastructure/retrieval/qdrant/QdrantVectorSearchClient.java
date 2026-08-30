package fmi.ethnowear.infrastructure.retrieval.qdrant;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import fmi.ethnowear.application.exception.RetrievalUnavailableException;
import fmi.ethnowear.application.port.retrieval.QueryEmbedding;
import fmi.ethnowear.application.port.retrieval.VectorSearchCandidate;
import fmi.ethnowear.application.port.retrieval.VectorSearchGateway;
import fmi.ethnowear.config.RetrievalClientProperties;
import fmi.ethnowear.config.RetrievalProperties;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Objects;

@Component
@ConditionalOnProperty(
        prefix = "ethnowear.retrieval",
        name = "enabled",
        havingValue = "true"
)
public class QdrantVectorSearchClient implements VectorSearchGateway {

    private final RestClient client;
    private final String collection;

    public QdrantVectorSearchClient(
            RetrievalClientProperties clientProperties,
            RetrievalProperties retrievalProperties
    ) {
        var requestFactory = new SimpleClientHttpRequestFactory();

        requestFactory.setConnectTimeout(retrievalProperties.qdrantTimeout());
        requestFactory.setReadTimeout(retrievalProperties.qdrantTimeout());

        this.client = RestClient.builder()
                .baseUrl(clientProperties.qdrantUrl())
                .requestFactory(requestFactory)
                .build();

        this.collection = clientProperties.qdrantCollection();
    }

    @Override
    public List<VectorSearchCandidate> search(@NonNull QueryEmbedding embedding, int candidateCount) {
        try {
            QueryResponse response = client.post()
                    .uri("/collections/{collection}/points/query", collection)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new QueryRequest(
                            embedding.values(),
                            candidateCount,
                            List.of("knowledgeChunkId", "contentHash"),
                            false
                    ))
                    .retrieve()
                    .body(QueryResponse.class);

            if (response == null || response.result() == null)
                throw unavailable();

            return response.result().points().stream()
                    .map(this::candidate)
                    .filter(Objects::nonNull)
                    .toList();
        } catch (RestClientException exception) {
            throw unavailable(exception);
        }
    }

    private @Nullable VectorSearchCandidate candidate(@NonNull QueryPoint point) {
        JsonNode payload = point.payload();

        if (payload == null)
            return null;

        JsonNode chunkId = payload.get("knowledgeChunkId");
        JsonNode contentHash = payload.get("contentHash");

        if (chunkId == null
                || !chunkId.canConvertToLong()
                || contentHash == null
                || !contentHash.isTextual())
            return null;

        return new VectorSearchCandidate(
                chunkId.longValue(),
                point.score(),
                contentHash.textValue()
        );
    }

    @Contract(" -> new")
    private @NonNull RetrievalUnavailableException unavailable() {
        return new RetrievalUnavailableException(
                "The vector search service is unavailable"
        );
    }

    @Contract("_ -> new")
    private @NonNull RetrievalUnavailableException unavailable(Throwable cause) {
        return new RetrievalUnavailableException(
                "The vector search service is unavailable",
                cause
        );
    }

    private record QueryRequest(
            List<Float> query,
            int limit,
            @JsonProperty("with_payload")
            List<String> withPayload,
            @JsonProperty("with_vector")
            boolean withVector
    ) {
    }

    private record QueryResponse(QueryResult result) {
    }

    private record QueryResult(List<QueryPoint> points) {

        private QueryResult {
            points = points == null ? List.of() : List.copyOf(points);
        }
    }

    private record QueryPoint(
            JsonNode id,
            double score,
            JsonNode payload
    ) {
    }
}