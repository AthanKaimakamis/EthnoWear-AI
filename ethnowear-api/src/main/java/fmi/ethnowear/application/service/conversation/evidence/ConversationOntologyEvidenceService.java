package fmi.ethnowear.application.service.conversation.evidence;

import fmi.ethnowear.application.dto.catalogue.EntityLinkDetails;
import fmi.ethnowear.application.dto.catalogue.EntityOntologyDetails;
import fmi.ethnowear.application.dto.catalogue.ConceptEvidenceSummaryDetails;
import fmi.ethnowear.application.dto.conversation.ConversationEntityCardDetails;
import fmi.ethnowear.application.model.conversation.ConversationOntologyEvidence;
import fmi.ethnowear.application.model.conversation.OntologyConversationEvidenceSelection;
import fmi.ethnowear.application.service.catalogue.OntologyEntityDetailReader;
import fmi.ethnowear.application.service.catalogue.ConceptCardEvidenceService;
import fmi.ethnowear.domain.model.ontology.FeatureType;
import fmi.ethnowear.util.TextUtils;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Unmodifiable;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class ConversationOntologyEvidenceService {

    private static final int MAXIMUM_MATCHES = 12;

    private final OntologyEntityDetailReader ontologyReader;
    private final ConceptCardEvidenceService conceptEvidenceService;

    public OntologyConversationEvidenceSelection resolve(String question, String language) {
        String normalizedQuestion = TextUtils.normalizeSearchText(question);

        List<EntityOntologyDetails> matches = Arrays.stream(FeatureType.values())
                .flatMap(type -> ontologyReader.list(type, language).stream())
                .filter(entity -> mentions(normalizedQuestion, entity))
                .limit(MAXIMUM_MATCHES)
                .toList();
        Map<FeatureType, Map<String, ConceptEvidenceSummaryDetails>> evidence =
                matches.stream()
                        .collect(java.util.stream.Collectors.groupingBy(
                                EntityOntologyDetails::entityType
                        ))
                        .entrySet()
                        .stream()
                        .collect(java.util.stream.Collectors.toUnmodifiableMap(
                                Map.Entry::getKey,
                                entry -> conceptEvidenceService.summarize(
                                        entry.getKey(),
                                        entry.getValue().stream()
                                                .map(EntityOntologyDetails::iri)
                                                .toList()
                                )
                        ));

        return new OntologyConversationEvidenceSelection(
                matches.stream().map(this::toEvidence).toList(),
                matches.stream()
                        .map(entity -> toCard(
                                entity,
                                evidence.getOrDefault(entity.entityType(), Map.of())
                                        .getOrDefault(
                                                entity.iri(),
                                                ConceptEvidenceSummaryDetails.empty()
                                        )
                        ))
                        .toList()
        );
    }

    private boolean mentions(String question, @NonNull EntityOntologyDetails entity) {
        return Stream.concat(
                        Stream.of(entity.localName(), entity.label()),
                        entity.altLabels().stream()
                )
                .filter(Objects::nonNull)
                .map(TextUtils::normalizeSearchText)
                .filter(value -> value.length() >= 4)
                .anyMatch(value -> question.contains(value) || tokenPrefixesMatch(question, value));
    }

    private boolean tokenPrefixesMatch(String question, String candidate) {
        List<String> questionTokens = tokens(question);
        List<String> candidateTokens = tokens(candidate);

        if (candidateTokens.isEmpty())
            return false;

        return candidateTokens.stream()
                .allMatch(candidateToken ->
                        questionTokens.stream()
                                .anyMatch(questionToken ->
                                        sameWordStem(questionToken, candidateToken))
                );
    }

    private boolean sameWordStem(@NonNull String left, String right) {
        if (left.equals(right))
            return true;

        int prefixLength = Math.min(5, Math.min(left.length(), right.length()));

        return prefixLength >= 4 && left.regionMatches(0, right, 0, prefixLength);
    }

    private @NonNull @Unmodifiable List<String> tokens(@NonNull String value) {
        return Arrays.stream(value.split("\\s+"))
                .filter(token -> token.length() >= 4)
                .toList();
    }

    private @NonNull ConversationOntologyEvidence toEvidence(@NonNull EntityOntologyDetails entity) {
        List<String> relationships = entity.relatedEntities()
                .entrySet()
                .stream()
                .flatMap(entry -> entry.getValue().stream()
                        .map(link -> relationship(entry.getKey(), link)))
                .toList();

        return new ConversationOntologyEvidence(
                "ontology:" + entity.entityType() + ":" + entity.localName(),
                entity.entityType(),
                entity.iri(),
                entity.localName(),
                entity.label(),
                entity.comment(),
                relationships
        );
    }

    private @NonNull String relationship(FeatureType type, @NonNull EntityLinkDetails link) {
        return type + ": " + link.label() + " [" + link.localName() + "]";
    }

    @Contract("_, _ -> new")
    private @NonNull ConversationEntityCardDetails toCard(
            @NonNull EntityOntologyDetails entity,
            @NonNull ConceptEvidenceSummaryDetails evidence
    ) {
        return new ConversationEntityCardDetails(
                entity.entityType(),
                entity.localName(),
                entity.label(),
                evidence.representativeMediaAssetId()
        );
    }
}
