package fmi.ethnowear.application.port.retrieval;

import java.util.Collection;

public interface VectorDeletionGateway {

    void deleteKnowledgeChunks(Collection<Long> knowledgeChunkIds);
}
