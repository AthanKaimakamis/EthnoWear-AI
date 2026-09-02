package fmi.ethnowear.application.service.conversation.action;

import fmi.ethnowear.application.dto.conversation.ConversationActionDetails;
import fmi.ethnowear.application.dto.conversation.ConversationArchiveFiltersDetails;
import fmi.ethnowear.application.model.conversation.ConversationEvidenceBundle;
import fmi.ethnowear.application.port.ontology.EmbroideryOntologyClient;
import fmi.ethnowear.domain.model.conversation.ConversationActionType;
import fmi.ethnowear.domain.model.conversation.ConversationArchiveTarget;
import fmi.ethnowear.domain.model.ontology.FeatureType;
import fmi.ethnowear.domain.model.ontology.LocalizedOntologyResource;
import fmi.ethnowear.domain.model.ontology.OntologyLanguage;
import fmi.ethnowear.util.TextUtils;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.Unmodifiable;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import static fmi.ethnowear.application.conversation.policy.ConversationPolicyTerms.ARCHIVE_DISCOVERY_TERM_PREFIXES;
import static fmi.ethnowear.application.conversation.policy.ConversationPolicyTerms.ARCHIVE_TARGET_TERMS;

@Component
@RequiredArgsConstructor
public class ConversationArchiveActionResolver {

    private final EmbroideryOntologyClient ontology;

    public List<ConversationActionDetails> resolve(
            String question,
            String language,
            @NonNull ConversationEvidenceBundle evidence
    ) {
        String normalizedQuestion = TextUtils.normalizeSearchText(question);

        if (!containsAny(normalizedQuestion, ARCHIVE_DISCOVERY_TERM_PREFIXES))
            return List.of();

        List<ConversationArchiveTarget> targets = ARCHIVE_TARGET_TERMS.entrySet()
                .stream()
                .filter(entry -> containsAny(normalizedQuestion, entry.getValue()))
                .map(Map.Entry::getKey)
                .toList();

        if (targets.size() != 1)
            return List.of();

        ConversationArchiveTarget target = targets.getFirst();
        OntologyLanguage ontologyLanguage = OntologyLanguage.fromTag(language);

        List<String> categories = categories(target, ontologyLanguage)
                .stream()
                .filter(category -> mentions(normalizedQuestion, category))
                .map(LocalizedOntologyResource::localName)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        Set<String> validEntities = entities(target, ontologyLanguage)
                .stream()
                .map(LocalizedOntologyResource::localName)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());

        List<String> entities = evidence.ontologyEvidence()
                .stream()
                .filter(item -> item.entityType() == target.featureType())
                .map(item -> item.localName())
                .filter(validEntities::contains)
                .distinct()
                .toList();

        List<String> regions = List.of();

        if (target == ConversationArchiveTarget.REGIONAL_EMBROIDERY
                || target == ConversationArchiveTarget.REGIONAL_MOTIF) {
            Set<String> validRegions = ontology.listLocalizedRegions(ontologyLanguage)
                    .stream()
                    .map(LocalizedOntologyResource::localName)
                    .filter(Objects::nonNull)
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());

            regions = evidence.ontologyEvidence()
                    .stream()
                    .filter(item -> item.entityType() == FeatureType.REGION)
                    .map(item -> item.localName())
                    .filter(validRegions::contains)
                    .distinct()
                    .toList();
        }

        return List.of(new ConversationActionDetails(
                ConversationActionType.OPEN_ARCHIVE_FILTER,
                label(target, language),
                target,
                new ConversationArchiveFiltersDetails(categories, entities, regions)
        ));
    }

    private @NonNull @Unmodifiable List<LocalizedOntologyResource> entities(
            @NonNull ConversationArchiveTarget target,
            OntologyLanguage language
    ) {
        return switch (target) {
            case REGIONAL_EMBROIDERY -> ontology.listLocalizedRegionalEmbroideryTypes(language);
            case REGIONAL_MOTIF -> ontology.listLocalizedRegionalMotifTypes(language);
            case MOTIF -> ontology.listLocalizedMotifs(language);
            case TECHNIQUE -> ontology.listLocalizedTechniques(language);
            case ORNAMENT -> ontology.listLocalizedOrnaments(language);
        };
    }

    private @NonNull @Unmodifiable List<LocalizedOntologyResource> categories(
            @NonNull ConversationArchiveTarget target,
            OntologyLanguage language
    ) {
        return switch (target) {
            case ORNAMENT -> ontology.listLocalizedOrnamentTypes(language);
            case TECHNIQUE -> ontology.listLocalizedTechniqueTypes(language);
            case REGIONAL_EMBROIDERY, REGIONAL_MOTIF, MOTIF -> List.of();
        };
    }

    private boolean mentions(String question, @NonNull LocalizedOntologyResource resource) {
        return Stream.concat(
                        Stream.of(resource.localName(), resource.label()),
                        resource.altLabels().stream()
                )
                .filter(Objects::nonNull)
                .map(TextUtils::normalizeSearchText)
                .filter(value -> value.length() >= 4)
                .anyMatch(value -> phraseMatches(question, value));
    }

    private boolean containsAny(String text, @NonNull List<String> terms) {
        return terms.stream()
                .map(TextUtils::normalizeSearchText)
                .anyMatch(term -> phraseMatches(text, term));
    }

    private boolean phraseMatches(String text, String phrase) {
        if ((" " + text + " ").contains(" " + phrase + " "))
            return true;

        List<String> textTokens = Arrays.asList(text.split(" "));
        List<String> phraseTokens = Arrays.asList(phrase.split(" "));

        if (phraseTokens.size() > 1) {
            return phraseTokens.stream()
                    .allMatch(phraseToken -> textTokens.stream()
                            .anyMatch(textToken -> sameStem(textToken, phraseToken)));
        }

        return textTokens.stream()
                .anyMatch(token -> token.startsWith(phrase));
    }

    private boolean sameStem(@NonNull String left, @NonNull String right) {
        if (left.equals(right))
            return true;

        int prefixLength = Math.min(5, Math.min(left.length(), right.length()));
        return prefixLength >= 4 && left.regionMatches(0, right, 0, prefixLength);
    }

    private @NonNull String label(@NonNull ConversationArchiveTarget target, String language) {
        boolean english = "en".equals(Locale.forLanguageTag(language).getLanguage());

        if (english) {
            return switch (target) {
                case REGIONAL_EMBROIDERY -> "Show regional embroideries";
                case REGIONAL_MOTIF -> "Show regional motifs";
                case MOTIF -> "Show motifs";
                case TECHNIQUE -> "Show techniques";
                case ORNAMENT -> "Show ornaments";
            };
        }

        return switch (target) {
            case REGIONAL_EMBROIDERY -> "Покажи регионалните шевици";
            case REGIONAL_MOTIF -> "Покажи регионалните мотиви";
            case MOTIF -> "Покажи мотивите";
            case TECHNIQUE -> "Покажи техниките";
            case ORNAMENT -> "Покажи орнаментите";
        };
    }
}
