package fmi.ethnowear.application.service.document.review;

import fmi.ethnowear.domain.model.document.indexing.IndexingState;
import fmi.ethnowear.persistence.jpa.entity.KnowledgeChunk;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPage;
import fmi.ethnowear.persistence.jpa.repository.KnowledgeChunkRepository;
import fmi.ethnowear.persistence.jpa.repository.document.KnowledgeChunkPageRepository;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class DocumentPageChunkInvalidator {

    private final KnowledgeChunkPageRepository chunkPageRepository;
    private final KnowledgeChunkRepository chunkRepository;

    public void invalidate(@NonNull DocumentPage page) {
        Map<Long, KnowledgeChunk> chunks = new LinkedHashMap<>();

        chunkPageRepository
                .findByDocumentPage_IdOrderByPageOrderAsc(page.getId())
                .forEach(link -> chunks.put(
                        link.getKnowledgeChunk().getId(),
                        link.getKnowledgeChunk()
                ));

        chunks.values().stream()
                .filter(this::canBecomeOutdated)
                .forEach(chunk -> chunk.setIndexingState(
                        IndexingState.OUTDATED
                ));

        chunkRepository.saveAll(chunks.values());

        if(page.getIndexingState() != IndexingState.NOT_ELIGIBLE)
            page.setIndexingState(IndexingState.OUTDATED);
    }

    private boolean canBecomeOutdated(@NonNull KnowledgeChunk chunk) {
        return chunk.getIndexingState() != IndexingState.NOT_ELIGIBLE
                && chunk.getIndexingState() != IndexingState.OUTDATED;
    }
}
