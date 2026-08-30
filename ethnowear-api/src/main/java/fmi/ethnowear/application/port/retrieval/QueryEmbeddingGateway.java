package fmi.ethnowear.application.port.retrieval;

public interface QueryEmbeddingGateway {

    QueryEmbedding embed(String question);
}
