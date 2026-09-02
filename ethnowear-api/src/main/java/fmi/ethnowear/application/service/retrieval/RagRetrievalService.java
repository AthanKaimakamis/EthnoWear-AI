package fmi.ethnowear.application.service.retrieval;

import fmi.ethnowear.application.dto.retrieval.*;
import fmi.ethnowear.application.exception.RetrievalUnavailableException;
import fmi.ethnowear.application.port.retrieval.*;
import fmi.ethnowear.config.RetrievalProperties;
import fmi.ethnowear.config.WorkerIndexingProperties;
import fmi.ethnowear.persistence.jpa.entity.KnowledgeChunk;
import fmi.ethnowear.persistence.jpa.entity.document.KnowledgeChunkPage;
import fmi.ethnowear.persistence.jpa.repository.KnowledgeChunkRepository;
import fmi.ethnowear.persistence.jpa.repository.document.KnowledgeChunkPageRepository;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Unmodifiable;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "ethnowear.retrieval",
        name = "enabled",
        havingValue = "true"
)
public class RagRetrievalService {

    private final RetrievalProperties properties;
    private final WorkerIndexingProperties indexingProperties;
    private final QueryEmbeddingGateway embeddingGateway;
    private final VectorSearchGateway vectorSearchGateway;
    private final KnowledgeChunkRepository chunkRepository;
    private final KnowledgeChunkPageRepository chunkPageRepository;
    private final RetrievalCandidateValidator validator;
    private final GroundedRetrievalMapper mapper;

    public GroundedRetrievalDetails retrieve(GroundedRetrievalQuery query) {
        if (!properties.enabled())
            throw new RetrievalUnavailableException("Grounded retrieval is currently unavailable");

        String question = requireQuestion(query);
        int resultCount = resultCount(query);
        QueryEmbedding embedding = embeddingGateway.embed(question);

        validateEmbedding(embedding);

        List<VectorSearchCandidate> candidates = boundedCandidates(
                vectorSearchGateway.search(embedding, properties.candidateCount(resultCount))
        );

        if (candidates.isEmpty())
            return insufficientEvidence(question);

        Map<Long, KnowledgeChunk> chunks = chunkRepository
                .findRetrievalCandidatesByIdIn(candidateIds(candidates))
                .stream()
                .collect(Collectors.toMap(KnowledgeChunk::getId, Function.identity()));

        Map<Long, List<KnowledgeChunkPage>> pages = chunkPageRepository
                .findRetrievalPagesByChunkIdIn(candidateIds(candidates))
                .stream()
                .collect(Collectors.groupingBy(
                        link -> link.getKnowledgeChunk().getId(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        List<GroundedPassageDetails> passages = candidates.stream()
                .map(candidate -> mapCandidate(candidate, chunks, pages))
                .filter(Objects::nonNull)
                .limit(resultCount)
                .toList();

        return new GroundedRetrievalDetails(
                question,
                passages.size(),
                passages,
                passages.isEmpty()
        );

    }


    private @Nullable GroundedPassageDetails mapCandidate(
            @NonNull VectorSearchCandidate candidate,
            @NonNull Map<Long, KnowledgeChunk> chunks,
            @NonNull Map<Long, List<KnowledgeChunkPage>> pages
    ) {
        KnowledgeChunk chunk = chunks.get(candidate.knowledgeChunkId());
        List<KnowledgeChunkPage> pageLinks = pages.getOrDefault(
                candidate.knowledgeChunkId(),
                List.of()
        );

        if (!validator.isEligible(chunk, candidate, pageLinks))
            return null;

        return mapper.toDetails(chunk, candidate, pageLinks);
    }

    @Contract("null -> fail")
    private @NonNull String requireQuestion(GroundedRetrievalQuery query) {
        if (query == null || query.question() == null || query.question().isBlank())
            throw new IllegalArgumentException("Retrieval question is required");

        String question = query.question().trim();

        if (question.length() > properties.maximumQuestionCharacters())
            throw new IllegalArgumentException("Retrieval question exceeds the configured limit");

        return question;
    }

    private int resultCount(@NonNull GroundedRetrievalQuery query) {
        int count = query.resultCount() == null
                ? properties.defaultResultCount()
                : query.resultCount();

        if (count <= 0 || count > properties.maximumResultCount())
            throw new IllegalArgumentException("Retrieval result count is invalid");

        return count;
    }

    private void validateEmbedding(QueryEmbedding embedding) {
        if (embedding == null
                || embedding.values() == null
                || embedding.model() == null
                || embedding.dimensions() != indexingProperties.expectedEmbeddingDimensions()
                || embedding.values().size() != embedding.dimensions()
                || !indexingProperties.expectedEmbeddingModel().equals(embedding.model())
                || embedding.values().stream()
                .anyMatch(value -> value == null || !Float.isFinite(value)))
            throw new RetrievalUnavailableException("The query embedding response is invalid");
    }

    @Contract("null -> fail")
    private @NonNull @Unmodifiable List<VectorSearchCandidate> boundedCandidates(List<VectorSearchCandidate> candidates) {
        if (candidates == null)
            throw new RetrievalUnavailableException("The vector search response is invalid");

        if (candidates.stream()
                .filter(Objects::nonNull)
                .mapToDouble(VectorSearchCandidate::similarity)
                .anyMatch(score -> !Double.isFinite(score) || score < -1.0 || score > 1.0))
            throw new RetrievalUnavailableException("The vector search response contains an invalid similarity score");

        return candidates.stream()
                .filter(Objects::nonNull)
                .filter(candidate -> candidate.knowledgeChunkId() != null)
                .filter(candidate -> candidate.similarity() >= properties.minimumSimilarity())
                .collect(Collectors.toMap(
                        VectorSearchCandidate::knowledgeChunkId,
                        Function.identity(),
                        (left, right) ->
                                left.similarity() >= right.similarity()
                                        ? left : right,
                        LinkedHashMap::new
                ))
                .values()
                .stream()
                .sorted(Comparator.comparingDouble(VectorSearchCandidate::similarity).reversed())
                .limit(properties.maximumCandidateCount())
                .toList();
    }

    private @NonNull GroundedRetrievalDetails insufficientEvidence(String question) {
        return new GroundedRetrievalDetails(question, 0, List.of(), true);
    }

    @Contract("_ -> !null")
    private @NonNull @Unmodifiable List<Long> candidateIds(@NonNull List<VectorSearchCandidate> candidates) {
        return candidates.stream()
                .map(VectorSearchCandidate::knowledgeChunkId)
                .toList();
    }
}
