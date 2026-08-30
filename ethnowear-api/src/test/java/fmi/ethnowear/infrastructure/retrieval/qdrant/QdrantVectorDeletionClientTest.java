package fmi.ethnowear.infrastructure.retrieval.qdrant;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class QdrantVectorDeletionClientTest {

    @Test
    void deletesDistinctKnowledgeChunkPointIds() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer
                .bindTo(builder)
                .build();

        server.expect(once(), requestTo(
                        "http://qdrant:6333/collections/chunks/points/delete?wait=true"
                ))
                .andExpect(content().json("{\"points\":[101,102]}"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        var client = new QdrantVectorDeletionClient(
                builder.baseUrl("http://qdrant:6333").build(),
                "chunks"
        );

        client.deleteKnowledgeChunks(List.of(101L, 102L, 101L));

        server.verify();
    }
}
