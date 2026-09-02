package fmi.ethnowear.application.service.conversation.generation;

import fmi.ethnowear.application.model.conversation.ConversationEvidenceBundle;
import fmi.ethnowear.application.model.conversation.ConversationGenerationResult;
import fmi.ethnowear.application.model.conversation.ConversationTurnExecutionContext;
import fmi.ethnowear.config.ConversationGenerationProperties;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
public class ConversationFallbackAnswerFactory {

    private final ConversationGenerationProperties properties;

    public ConversationGenerationResult create(
            @NonNull ConversationTurnExecutionContext context,
            @NonNull ConversationEvidenceBundle evidence,
            @NonNull List<String> warningCodes
    ) {
        List<String> citations = Stream.concat(
                        Stream.concat(
                                evidence.documentPassages()
                                        .stream()
                                        .map(passage -> "chunk:" + passage.chunkId()),
                                evidence.ontologyEvidence()
                                        .stream()
                                        .map(item -> item.citationId())
                        ),
                        evidence.archiveEvidence()
                                .stream()
                                .map(item -> item.citationId())
                )
                .distinct()
                .limit(properties.maximumCitations())
                .toList();

        boolean insufficientEvidence = evidence.isEmpty();

        String answer = message(context.language(), insufficientEvidence);

        return new ConversationGenerationResult(
                answer,
                insufficientEvidence,
                List.of(),
                citations,
                Stream.concat(
                                warningCodes.stream(),
                                Stream.of("LANGUAGE_MODEL_UNAVAILABLE")
                        )
                        .distinct()
                        .toList()
        );
    }

    public ConversationGenerationResult createAfterRejected(
            @NonNull ConversationTurnExecutionContext context,
            @NonNull ConversationEvidenceBundle evidence,
            @NonNull List<String> warningCodes
    ) {
        List<String> citations = citations(evidence);
        boolean insufficientEvidence = evidence.isEmpty();

        return new ConversationGenerationResult(
                rejectedMessage(context.userMessage(), context.language(), evidence, insufficientEvidence),
                insufficientEvidence,
                List.of(),
                citations,
                Stream.concat(warningCodes.stream(), Stream.of("LANGUAGE_MODEL_RESPONSE_REJECTED"))
                        .distinct()
                        .toList()
        );
    }

    private List<String> citations(ConversationEvidenceBundle evidence) {
        return Stream.concat(
                        Stream.concat(
                                evidence.documentPassages().stream().map(passage -> "chunk:" + passage.chunkId()),
                                evidence.ontologyEvidence().stream().map(item -> item.citationId())
                        ),
                        evidence.archiveEvidence().stream().map(item -> item.citationId())
                )
                .distinct()
                .limit(properties.maximumCitations())
                .toList();
    }

    private String rejectedMessage(
            String question,
            String language,
            ConversationEvidenceBundle evidence,
            boolean insufficientEvidence
    ) {
        if (insufficientEvidence)
            return message(language, true);

        String normalizedQuestion = question.toLowerCase(java.util.Locale.ROOT);
        String relationshipPrefix = normalizedQuestion.contains("техник") || normalizedQuestion.contains("techni")
                ? "TECHNIQUE: "
                : normalizedQuestion.contains("орнамент") || normalizedQuestion.contains("ornament")
                ? "ORNAMENT: "
                : null;

        List<String> relatedLabels = relationshipPrefix == null
                ? List.of()
                : evidence.ontologyEvidence().stream()
                        .flatMap(item -> item.relationships().stream())
                        .filter(relationship -> relationship.startsWith(relationshipPrefix))
                        .map(relationship -> relationship.substring(relationshipPrefix.length()))
                        .map(relationship -> relationship.replaceFirst("\\s*\\[[^]]+]$", ""))
                        .distinct()
                        .limit(8)
                        .toList();

        if (!relatedLabels.isEmpty()) {
            String related = String.join(", ", relatedLabels);
            return "en".equalsIgnoreCase(language)
                    ? "The verified related " + (relationshipPrefix.startsWith("TECHNIQUE") ? "techniques" : "ornaments") + " are: " + related + "."
                    : "Проверените свързани " + (relationshipPrefix.startsWith("TECHNIQUE") ? "техники" : "орнаменти") + " са: " + related + ".";
        }

        List<String> labels = evidence.ontologyEvidence().stream()
                .map(item -> item.label())
                .filter(label -> label != null && !label.isBlank())
                .distinct()
                .limit(8)
                .toList();

        String concepts = labels.isEmpty() ? "" : String.join(", ", labels);

        if ("en".equalsIgnoreCase(language))
            return concepts.isEmpty()
                    ? "I found verified evidence, shown below, but could not safely formulate a complete answer."
                    : "The verified related concepts are: " + concepts + ". The supporting evidence is shown below.";

        return concepts.isEmpty()
                ? "Намерени са проверени данни, показани по-долу, но не може безопасно да се формулира пълен отговор."
                : "Проверените свързани понятия са: " + concepts + ". Подкрепящите данни са показани по-долу.";
    }

    @Contract(pure = true)
    private @NonNull String message(String language, boolean insufficientEvidence) {
        if ("en".equalsIgnoreCase(language)) {
            return insufficientEvidence
                    ? "There is not enough verified evidence to answer this question."
                    : "The language service is temporarily unavailable. The verified sources and related concepts found for this question are shown below.";
        }

        return insufficientEvidence
                ? "Няма достатъчно проверени данни, за да се отговори на този въпрос."
                : "Езиковата услуга временно не е достъпна. По-долу са показани намерените проверени източници и свързани понятия.";
    }
}
