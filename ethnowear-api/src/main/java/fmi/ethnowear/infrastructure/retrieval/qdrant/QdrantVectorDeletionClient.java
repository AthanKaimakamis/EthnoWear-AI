package fmi.ethnowear.infrastructure.retrieval.qdrant;

import fmi.ethnowear.application.exception.RetrievalUnavailableException;
import fmi.ethnowear.application.port.retrieval.VectorDeletionGateway;
import fmi.ethnowear.config.RetrievalClientProperties;
import fmi.ethnowear.config.RetrievalProperties;
import org.jspecify.annotations.NonNull;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Collection;
import java.util.List;

@Component
public class QdrantVectorDeletionClient implements VectorDeletionGateway {

    private final RestClient client;
    private final String collection;

    @Autowired
    public QdrantVectorDeletionClient(
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

    QdrantVectorDeletionClient(RestClient client, String collection) {
        this.client = client;
        this.collection = collection;
    }

    @Override
    public void deleteKnowledgeChunks(
            @NonNull Collection<Long> knowledgeChunkIds
    ) {
        List<Long> pointIds = knowledgeChunkIds.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();

        if(pointIds.isEmpty())
            return;

        try {
            client.post()
                    .uri(
                            "/collections/{collection}/points/delete?wait=true",
                            collection
                    )
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new DeletePointsRequest(pointIds))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException ex) {
            throw new RetrievalUnavailableException(
                    "Vector cleanup is currently unavailable",
                    ex
            );
        }
    }

    private record DeletePointsRequest(List<Long> points) {
    }
}
