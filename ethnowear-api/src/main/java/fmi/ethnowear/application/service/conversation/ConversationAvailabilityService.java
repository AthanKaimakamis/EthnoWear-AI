package fmi.ethnowear.application.service.conversation;

import com.fasterxml.jackson.databind.JsonNode;
import fmi.ethnowear.application.dto.conversation.ConversationAvailabilityDetails;
import fmi.ethnowear.config.ConversationGenerationProperties;
import fmi.ethnowear.config.RetrievalClientProperties;
import fmi.ethnowear.config.RetrievalProperties;
import fmi.ethnowear.config.WorkerIndexingProperties;
import org.jspecify.annotations.NonNull;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Service
public class ConversationAvailabilityService {
    private static final Duration CHECK_TIMEOUT = Duration.ofSeconds(3);
    private final ConversationGenerationProperties generation;
    private final RetrievalProperties retrieval;
    private final RetrievalClientProperties retrievalClient;
    private final WorkerIndexingProperties indexing;
    private final RestClient generationOllama;
    private final RestClient retrievalOllama;
    private final RestClient qdrant;

    public ConversationAvailabilityService(ConversationGenerationProperties generation, RetrievalProperties retrieval,
                                           RetrievalClientProperties retrievalClient, WorkerIndexingProperties indexing) {
        this.generation = generation;
        this.retrieval = retrieval;
        this.retrievalClient = retrievalClient;
        this.indexing = indexing;
        this.generationOllama = client(generation.ollamaBaseUrl());
        this.retrievalOllama = client(retrievalClient.ollamaBaseUrl());
        this.qdrant = client(retrievalClient.qdrantUrl());
    }

    public ConversationAvailabilityDetails check() {
        boolean ollamaAvailable = generation.enabled() && modelAvailable(generationOllama, generation.model());
        boolean ragAvailable = retrieval.enabled()
                && modelAvailable(retrievalOllama, indexing.expectedEmbeddingModel())
                && collectionAvailable();
        List<String> codes = new ArrayList<>();
        if (!ollamaAvailable) codes.add("CONVERSATION_OLLAMA_UNAVAILABLE");
        if (!ragAvailable) codes.add("CONVERSATION_RAG_UNAVAILABLE");
        return new ConversationAvailabilityDetails(ollamaAvailable && ragAvailable, ollamaAvailable, ragAvailable, codes);
    }

    private boolean modelAvailable(@NonNull RestClient client, String expectedModel) {
        try {
            JsonNode response = client.get().uri("/api/tags").retrieve().body(JsonNode.class);
            if (response == null || !response.path("models").isArray()) return false;
            for (JsonNode model : response.path("models")) {
                if (sameModel(expectedModel, model.path("name").asText())
                        || sameModel(expectedModel, model.path("model").asText())) return true;
            }
            return false;
        } catch (RestClientException exception) { return false; }
    }

    private boolean sameModel(@NonNull String expectedModel, String availableModel) {
        return expectedModel.equals(availableModel)
                || (!expectedModel.contains(":") && (expectedModel + ":latest").equals(availableModel));
    }

    private boolean collectionAvailable() {
        try {
            JsonNode response = qdrant.get().uri("/collections/{collection}", retrievalClient.qdrantCollection()).retrieve().body(JsonNode.class);
            return response != null && "ok".equalsIgnoreCase(response.path("status").asText());
        } catch (RestClientException exception) { return false; }
    }

    private @NonNull RestClient client(String baseUrl) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CHECK_TIMEOUT);
        factory.setReadTimeout(CHECK_TIMEOUT);
        return RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }
}
