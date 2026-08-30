package fmi.ethnowear.application.port.retrieval;

import java.util.List;

public record QueryEmbedding(
        List<Float> values,
        String model,
        int dimensions
) {

    public QueryEmbedding {
        values = List.copyOf(values);
    }
}