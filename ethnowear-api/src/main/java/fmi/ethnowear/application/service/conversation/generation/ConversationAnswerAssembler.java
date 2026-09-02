package fmi.ethnowear.application.service.conversation.generation;

import fmi.ethnowear.application.dto.conversation.*;
import fmi.ethnowear.application.dto.retrieval.GroundedPassageDetails;
import fmi.ethnowear.application.dto.retrieval.GroundedSourceCitationDetails;
import fmi.ethnowear.application.exception.ConversationGenerationRejectedException;
import fmi.ethnowear.application.model.conversation.ConversationEvidenceBundle;
import fmi.ethnowear.application.model.conversation.ConversationGenerationResult;
import fmi.ethnowear.application.model.conversation.ConversationArchiveEvidence;
import fmi.ethnowear.application.model.conversation.ConversationOntologyEvidence;
import fmi.ethnowear.application.model.conversation.ConversationTurnExecutionContext;
import fmi.ethnowear.application.service.conversation.action.ConversationArchiveActionResolver;
import fmi.ethnowear.domain.model.ontology.FeatureType;
import org.jetbrains.annotations.Unmodifiable;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

@Component
public class ConversationAnswerAssembler {

    private final ConversationArchiveActionResolver actionResolver;

    public ConversationAnswerAssembler(ConversationArchiveActionResolver actionResolver) {
        this.actionResolver = actionResolver;
    }
    public ConversationAnswerDetails assemble(
            @NonNull ConversationTurnExecutionContext context,
            @NonNull ConversationEvidenceBundle evidence,
            @NonNull ConversationGenerationResult generation
    ) {
        Map<String, GroundedPassageDetails> documentEvidence = documentEvidence(evidence);

        Map<String, ConversationOntologyEvidence> ontologyEvidence = ontologyEvidence(evidence);

        Map<String, ConversationArchiveEvidence> archiveEvidence = archiveEvidence(evidence);

        List<String> citations = generation.citedEvidenceIds()
                .stream()
                .distinct()
                .toList();

        validateCitations(
                citations,
                documentEvidence,
                ontologyEvidence,
                archiveEvidence,
                generation.insufficientEvidence()
        );

        boolean insufficientEvidence = generation.insufficientEvidence() || evidence.isEmpty();

        List<ConversationEntityCardDetails> entityCards = entityCards(citations, ontologyEvidence, evidence);

        List<ConversationArchiveCardDetails> archiveCards = archiveCards(citations, archiveEvidence, evidence);

        List<ConversationMediaDetails> media = media(entityCards, archiveCards, evidence);

        return new ConversationAnswerDetails(
                context.conversationId(),
                context.turnId(),
                generation.answer(),
                insufficientEvidence,
                sources(citations, documentEvidence),
                entityCards,
                archiveCards,
                media,
                actionResolver.resolve(
                        context.userMessage(),
                        context.language(),
                        evidence
                ),
                warningCodes(evidence, generation, insufficientEvidence)
        );
    }

    private @NonNull @Unmodifiable Map<String, GroundedPassageDetails> documentEvidence(@NonNull ConversationEvidenceBundle evidence) {
        Map<String, GroundedPassageDetails> result = new LinkedHashMap<>();

        evidence.documentPassages().forEach(
                passage -> result.put(
                        "chunk:" + passage.chunkId(),
                        passage
                )
        );

        return Map.copyOf(result);
    }

    private @NonNull @Unmodifiable Map<String, ConversationOntologyEvidence> ontologyEvidence(@NonNull ConversationEvidenceBundle evidence) {
        Map<String, ConversationOntologyEvidence> result = new LinkedHashMap<>();

        evidence.ontologyEvidence().forEach(
                item -> result.put(item.citationId(), item)
        );

        return Map.copyOf(result);
    }

    private @NonNull @Unmodifiable Map<String, ConversationArchiveEvidence> archiveEvidence(
            @NonNull ConversationEvidenceBundle evidence
    ) {
        Map<String, ConversationArchiveEvidence> result = new LinkedHashMap<>();

        evidence.archiveEvidence().forEach(item -> result.put(item.citationId(), item));

        return Map.copyOf(result);
    }

    private void validateCitations(
            @NonNull List<String> citations,
            @NonNull Map<String, GroundedPassageDetails> documents,
            @NonNull Map<String, ConversationOntologyEvidence> ontology,
            @NonNull Map<String, ConversationArchiveEvidence> archives,
            boolean insufficientEvidence
    ) {
        boolean unknownCitation = citations.stream()
                .anyMatch(citation -> !documents.containsKey(citation)
                        && !ontology.containsKey(citation)
                        && !archives.containsKey(citation));

        if (unknownCitation)
            throw new ConversationGenerationRejectedException("Generated answer references unknown evidence");

        if (!insufficientEvidence
                && citations.isEmpty()
                && (!documents.isEmpty() || !ontology.isEmpty() || !archives.isEmpty()))
            throw new ConversationGenerationRejectedException("Grounded answer must cite its evidence");
    }

    private @NonNull @Unmodifiable List<ConversationSourceDetails> sources(
            @NonNull List<String> citations,
            @NonNull Map<String, GroundedPassageDetails> documents
    ) {
        return citations.stream()
                .filter(documents::containsKey)
                .map(citation -> toSource(
                        citation,
                        documents.get(citation)
                ))
                .toList();
    }

    private @NonNull ConversationSourceDetails toSource(String citationId, @NonNull GroundedPassageDetails passage) {
        GroundedSourceCitationDetails source = passage.pages()
                .stream()
                .map(page -> page.source())
                .filter(item -> item != null)
                .findFirst()
                .orElse(null);

        return new ConversationSourceDetails(
                citationId,
                source == null ? null : source.sourceId(),
                source == null
                        ? passage.documentTitle()
                        : source.sourceTitle(),
                source == null ? null : source.author()
        );
    }

    private @NonNull @Unmodifiable List<ConversationEntityCardDetails> entityCards(
            @NonNull List<String> citations,
            @NonNull Map<String, ConversationOntologyEvidence> ontology,
            @NonNull ConversationEvidenceBundle evidence
    ) {
        Set<EntityIdentity> selected = citations.stream()
                .map(ontology::get)
                .filter(item -> item != null)
                .map(item -> new EntityIdentity(
                        item.entityType(),
                        item.localName()
                ))
                .collect(
                        LinkedHashSet::new,
                        LinkedHashSet::add,
                        LinkedHashSet::addAll
                );

        return evidence.entityCards()
                .stream()
                .filter(card -> selected.contains(
                        new EntityIdentity(
                                card.entityType(),
                                card.localName()
                        )
                ))
                .toList();
    }

    private @NonNull @Unmodifiable List<ConversationArchiveCardDetails> archiveCards(
            @NonNull List<String> citations,
            @NonNull Map<String, ConversationArchiveEvidence> archiveEvidence,
            @NonNull ConversationEvidenceBundle evidence
    ) {
        Set<Long> selected = citations.stream()
                .map(archiveEvidence::get)
                .filter(java.util.Objects::nonNull)
                .map(ConversationArchiveEvidence::archiveItemId)
                .collect(java.util.stream.Collectors.toSet());

        return evidence.archiveCards().stream()
                .filter(card -> selected.contains(card.archiveItemId()))
                .toList();
    }

    private @NonNull @Unmodifiable List<ConversationMediaDetails> media(
            @NonNull List<ConversationEntityCardDetails> entityCards,
            @NonNull List<ConversationArchiveCardDetails> archiveCards,
            @NonNull ConversationEvidenceBundle evidence
    ) {
        Set<Long> allowedMediaIds = Stream.concat(
                        entityCards.stream()
                                .map(ConversationEntityCardDetails::representativeMediaAssetId),
                        archiveCards.stream()
                                .map(ConversationArchiveCardDetails::representativeMediaAssetId)
                )
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());

        return evidence.media()
                .stream()
                .filter(item -> allowedMediaIds.contains(item.mediaAssetId()))
                .distinct()
                .toList();
    }

    private @NonNull @Unmodifiable List<String> warningCodes(
            @NonNull ConversationEvidenceBundle evidence,
            @NonNull ConversationGenerationResult generation,
            boolean insufficientEvidence
    ) {
        Stream<String> warnings = Stream.concat(
                evidence.warningCodes().stream(),
                generation.warningCodes().stream()
        );

        if (insufficientEvidence)
            warnings = Stream.concat(warnings, Stream.of("INSUFFICIENT_EVIDENCE"));

        return warnings.distinct().toList();
    }

    private record EntityIdentity(
            FeatureType entityType,
            String localName
    ) {
    }
}
