package fmi.ethnowear.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ethnowear.retrieval.client")
public record RetrievalClientProperties(
        String ollamaBaseUrl,
        String qdrantUrl,
        String qdrantCollection
) {
}
