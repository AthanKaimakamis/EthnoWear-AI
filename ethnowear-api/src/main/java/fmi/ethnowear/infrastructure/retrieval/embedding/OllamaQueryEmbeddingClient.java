package fmi.ethnowear.infrastructure.retrieval.embedding;

import fmi.ethnowear.application.exception.RetrievalUnavailableException;
import fmi.ethnowear.application.port.retrieval.QueryEmbedding;
import fmi.ethnowear.application.port.retrieval.QueryEmbeddingGateway;
import fmi.ethnowear.config.RetrievalClientProperties;
import fmi.ethnowear.config.RetrievalProperties;
import fmi.ethnowear.config.WorkerIndexingProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

@Component
public class OllamaQueryEmbeddingClient implements QueryEmbeddingGateway {

    private final RestClient client;
    private final WorkerIndexingProperties indexingProperties;

    @Autowired
    public OllamaQueryEmbeddingClient(
            RetrievalClientProperties clientProperties,
            RetrievalProperties retrievalProperties,
            WorkerIndexingProperties indexingProperties
    ) {
        this(
                buildClient(clientProperties, retrievalProperties),
                indexingProperties
        );
    }

    OllamaQueryEmbeddingClient(
            RestClient client,
            WorkerIndexingProperties indexingProperties
    ) {
        this.client = client;
        this.indexingProperties = indexingProperties;
    }

    private static @NonNull RestClient buildClient(
            @NonNull RetrievalClientProperties clientProperties,
            @NonNull RetrievalProperties retrievalProperties
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();

        requestFactory.setConnectTimeout(retrievalProperties.embeddingTimeout());
        requestFactory.setReadTimeout(retrievalProperties.embeddingTimeout());

        return RestClient.builder()
                .baseUrl(clientProperties.ollamaBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public QueryEmbedding embed(String question) {
        try {
            OllamaEmbeddingResponse response = client.post()
                    .uri("/api/embed")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new OllamaEmbeddingRequest(
                            indexingProperties.expectedEmbeddingModel(),
                            question,
                            false
                    ))
                    .retrieve()
                    .body(OllamaEmbeddingResponse.class);

            if (response == null
                    || response.embeddings() == null
                    || response.embeddings().size() != 1)
                throw unavailable();

            List<Float> values = response.embeddings().getFirst();

            if (values == null)
                throw unavailable();

            return new QueryEmbedding(
                    values,
                    indexingProperties.expectedEmbeddingModel(),
                    values.size()
            );
        } catch (RestClientException exception) {
            throw unavailable(exception);
        }
    }

    @Contract(" -> new")
    private @NonNull RetrievalUnavailableException unavailable() {
        return new RetrievalUnavailableException("The query embedding service is unavailable");
    }

    @Contract("_ -> new")
    private @NonNull RetrievalUnavailableException unavailable(Throwable cause) {
        return new RetrievalUnavailableException("The query embedding service is unavailable", cause);
    }

    private record OllamaEmbeddingRequest(
            String model,
            String input,
            boolean truncate
    ) {
    }

    private record OllamaEmbeddingResponse(
            List<List<Float>> embeddings
    ) {
    }
}
