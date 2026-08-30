package fmi.ethnowear.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

@ConfigurationProperties(prefix = "ethnowear.worker-api.indexing")
public record WorkerIndexingProperties(
        int maximumContentCharacters,
        int maximumEmbeddingDimensions,
        int maximumEmbeddingModelCharacters,
        int maximumVectorCollectionCharacters,
        int maximumVectorPointIdCharacters,
        String expectedEmbeddingModel,
        int expectedEmbeddingDimensions,
        String expectedVectorCollection
) {

    @ConstructorBinding
    public WorkerIndexingProperties {
        if (maximumContentCharacters <= 0
                || maximumEmbeddingDimensions <= 0
                || maximumEmbeddingModelCharacters <= 0
                || maximumVectorCollectionCharacters <= 0
                || maximumVectorPointIdCharacters <= 0)
            throw new IllegalStateException("Worker indexing limits must be positive");

        if (maximumEmbeddingModelCharacters > 100
                || maximumVectorCollectionCharacters > 150
                || maximumVectorPointIdCharacters > 255)
            throw new IllegalStateException(
                    "Worker indexing limits exceed database column limits"
            );

        requireText(expectedEmbeddingModel, "Expected embedding model");
        requireText(expectedVectorCollection, "Expected vector collection");

        if (expectedEmbeddingDimensions <= 0
                || expectedEmbeddingDimensions > maximumEmbeddingDimensions)
            throw new IllegalStateException(
                    "Expected embedding dimensions exceed the configured limit"
            );

        if (expectedEmbeddingModel.length() > maximumEmbeddingModelCharacters
                || expectedVectorCollection.length() > maximumVectorCollectionCharacters)
            throw new IllegalStateException(
                    "Expected indexing identifiers exceed the configured limits"
            );
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank())
            throw new IllegalStateException(field + " is required");
    }
}
