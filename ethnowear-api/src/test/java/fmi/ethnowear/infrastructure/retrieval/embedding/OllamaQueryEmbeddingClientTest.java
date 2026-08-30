package fmi.ethnowear.infrastructure.retrieval.embedding;

import fmi.ethnowear.application.port.retrieval.QueryEmbedding;
import fmi.ethnowear.config.WorkerIndexingProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OllamaQueryEmbeddingClientTest {

    @Test
    void requestsConfiguredOllamaModelAndReturnsEmbedding() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer
                .bindTo(builder)
                .build();

        server.expect(once(), requestTo("http://ollama:11434/api/embed"))
                .andExpect(content().json("""
                        {
                          "model": "bge-m3",
                          "input": "Какво е шопска шевица?",
                          "truncate": false
                        }
                        """))
                .andRespond(withSuccess(
                        "{\"embeddings\":[[0.1,0.2,0.3]]}",
                        MediaType.APPLICATION_JSON
                ));

        OllamaQueryEmbeddingClient client = new OllamaQueryEmbeddingClient(
                builder.baseUrl("http://ollama:11434").build(),
                new WorkerIndexingProperties(
                        100_000,
                        4_096,
                        100,
                        150,
                        255,
                        "bge-m3",
                        1_024,
                        "chunks"
                )
        );

        QueryEmbedding result = client.embed("Какво е шопска шевица?");

        assertEquals("bge-m3", result.model());
        assertEquals(3, result.dimensions());
        assertEquals(3, result.values().size());
        server.verify();
    }
}
