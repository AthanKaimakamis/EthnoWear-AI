package fmi.ethnowear.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

import java.time.Duration;

@ConfigurationProperties(prefix = "ethnowear.retrieval")
public record RetrievalProperties(
        boolean enabled,
        int defaultResultCount,
        int maximumResultCount,
        int candidateMultiplier,
        int maximumCandidateCount,
        int maximumQuestionCharacters,
        Duration embeddingTimeout,
        Duration qdrantTimeout
) {

    @ConstructorBinding
    public RetrievalProperties {
        if (defaultResultCount <= 0
                || maximumResultCount < defaultResultCount
                || candidateMultiplier <= 1
                || maximumCandidateCount < maximumResultCount
                || maximumQuestionCharacters <= 0)
            throw new IllegalStateException("Invalid retrieval limits");

        if (embeddingTimeout == null
                || embeddingTimeout.isZero()
                || embeddingTimeout.isNegative()
                || qdrantTimeout == null
                || qdrantTimeout.isZero()
                || qdrantTimeout.isNegative())
            throw new IllegalStateException("Retrieval timeouts must be positive");
    }

    public int candidateCount(int resultCount) {
        return Math.min(
                maximumCandidateCount,
                Math.multiplyExact(resultCount, candidateMultiplier)
        );
    }
}