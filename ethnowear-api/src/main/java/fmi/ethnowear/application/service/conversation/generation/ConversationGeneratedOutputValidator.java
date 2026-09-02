package fmi.ethnowear.application.service.conversation.generation;

import fmi.ethnowear.application.exception.ConversationGenerationRejectedException;
import fmi.ethnowear.application.model.conversation.ConversationGenerationRequest;
import fmi.ethnowear.application.model.conversation.ConversationGenerationResult;
import fmi.ethnowear.application.model.conversation.ConversationGeneratedClaim;
import fmi.ethnowear.config.ConversationGenerationProperties;
import fmi.ethnowear.util.TextUtils;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class ConversationGeneratedOutputValidator {

    private static final int MAXIMUM_VERBATIM_WORDS = 20;

    private static final List<String> INTERNAL_TERMS = List.of(
            "vectorpointid",
            "vectorcollection",
            "contenthash",
            "storagekey",
            "filepath",
            "activejobkey",
            "system prompt",
            "developer message",
            "worker token"
    );

    private static final Pattern URL = Pattern.compile("(?i)(?:https?://|www\\.)\\S+");

    private static final Pattern HTML = Pattern.compile("(?i)<\\/?[a-z][^>]*>");

    private static final Pattern CYRILLIC = Pattern.compile("\\p{IsCyrillic}");

    private static final Pattern LATIN = Pattern.compile("\\p{IsLatin}");

    private static final Set<String> GROUNDING_STOP_WORDS = Set.of(
            "това", "този", "тази", "тези", "като", "които", "която",
            "което", "както", "може", "бъде", "били", "било", "била",
            "също", "между", "според", "източник", "източникът", "описва",
            "показва", "представя", "данни", "информация", "there", "this",
            "that", "with", "from", "source", "evidence", "describes", "shows"
    );

    private final ConversationGenerationProperties properties;

    public void validate(
            @NonNull ConversationGenerationRequest request,
            @NonNull ConversationGenerationResult result
    ) {
        validateAnswer(request.language(), result.answer());
        validateCitations(result.citedEvidenceIds());
        validateClaims(request, result);
        validateWarnings(result.warningCodes());
        validateVerbatimOverlap(request, result.answer());
    }

    private void validateAnswer(@NonNull String language, @NonNull String answer) {
        if (answer.length() > properties.maximumAnswerCharacters())
            throw unavailable("Generated answer exceeds the configured limit");

        String normalized = answer.toLowerCase(Locale.ROOT);

        if (INTERNAL_TERMS.stream().anyMatch(normalized::contains))
            throw unavailable("Generated answer contains internal information");

        if (URL.matcher(answer).find())
            throw unavailable("Generated answer contains an unsupported URL");

        if (HTML.matcher(answer).find())
            throw unavailable("Generated answer contains HTML");

        String languageCode = Locale.forLanguageTag(language).getLanguage();

        if ("bg".equals(languageCode) && !CYRILLIC.matcher(answer).find())
            throw unavailable("Generated answer does not use the requested language");

        if ("en".equals(languageCode) && !LATIN.matcher(answer).find())
            throw unavailable("Generated answer does not use the requested language");
    }

    private void validateCitations(@NonNull List<String> citations) {
        if (citations.size() > properties.maximumCitations())
            throw unavailable("Generated answer contains too many citations");

        if (citations.stream().anyMatch(value -> value == null || value.isBlank() || value.length() > 200))
            throw unavailable("Generated answer contains invalid citations");
    }

    private void validateWarnings(@NonNull List<String> warnings) {
        if (warnings.stream().anyMatch(value -> value == null || !value.matches("[A-Z0-9_]{1,100}")))
            throw unavailable("Generated answer contains invalid warning codes");
    }

    private void validateClaims(
            @NonNull ConversationGenerationRequest request,
            @NonNull ConversationGenerationResult result
    ) {
        List<ConversationGeneratedClaim> claims = result.claims();

        if (claims.size() > properties.maximumCitations())
            throw unavailable("Generated answer contains too many claim segments");

        if (!result.insufficientEvidence()
                && !request.evidence().isEmpty()
                && claims.isEmpty())
            throw unavailable("Grounded answer must contain evidence-linked claims");

        if (!claims.isEmpty()) {
            String assembledAnswer = claims.stream()
                    .map(ConversationGeneratedClaim::text)
                    .map(value -> value == null ? "" : value.trim())
                    .collect(java.util.stream.Collectors.joining(" "));

            if (!assembledAnswer.equals(result.answer()))
                throw unavailable("Generated answer does not match its claim segments");
        }

        Map<String, String> evidence = evidenceText(request);
        Set<String> claimCitations = new LinkedHashSet<>();

        for (ConversationGeneratedClaim claim : claims) {
            if (claim == null || claim.text() == null || claim.text().isBlank())
                throw unavailable("Generated answer contains an invalid claim segment");

            if (claim.evidenceIds().isEmpty())
                throw unavailable("Generated factual claim has no supporting evidence");

            if (claim.evidenceIds().stream().anyMatch(id -> !evidence.containsKey(id)))
                throw unavailable("Generated factual claim references unknown evidence");

            if (!isSupported(claim, evidence))
                throw unavailable("Generated factual claim is not supported by its cited evidence");

            claimCitations.addAll(claim.evidenceIds());
        }

        if (!claimCitations.equals(new LinkedHashSet<>(result.citedEvidenceIds())))
            throw unavailable("Generated citations do not match claim evidence");
    }

    private @NonNull Map<String, String> evidenceText(
            @NonNull ConversationGenerationRequest request
    ) {
        Map<String, String> result = new LinkedHashMap<>();

        request.evidence().documentPassages().forEach(passage -> result.put(
                "chunk:" + passage.chunkId(),
                String.join(" ", safe(passage.excerpt()), safe(passage.documentTitle()))
        ));
        request.evidence().ontologyEvidence().forEach(item -> result.put(
                item.citationId(),
                String.join(
                        " ",
                        safe(item.localName()),
                        safe(item.label()),
                        safe(item.description()),
                        String.join(" ", item.relationships())
                )
        ));
        request.evidence().archiveEvidence().forEach(item -> result.put(
                item.citationId(),
                String.join(
                        " ",
                        safe(item.title()),
                        safe(item.description()),
                        item.archiveType() == null ? "" : item.archiveType().name(),
                        safe(item.periodText()),
                        safe(item.originText()),
                        safe(item.currentLocation())
                )
        ));

        return Map.copyOf(result);
    }

    private boolean isSupported(
            @NonNull ConversationGeneratedClaim claim,
            @NonNull Map<String, String> evidence
    ) {
        Set<String> claimTokens = groundingTokens(claim.text());
        Set<String> evidenceTokens = claim.evidenceIds().stream()
                .map(evidence::get)
                .flatMap(value -> groundingTokens(value).stream())
                .collect(java.util.stream.Collectors.toSet());

        if (claimTokens.isEmpty() || evidenceTokens.isEmpty())
            return false;

        long matches = claimTokens.stream()
                .filter(claimToken -> evidenceTokens.stream()
                        .anyMatch(evidenceToken -> sameStem(claimToken, evidenceToken)))
                .count();
        long requiredMatches = Math.min(2, claimTokens.size());
        double coverage = (double) matches / claimTokens.size();

        return matches >= requiredMatches && coverage >= 0.5;
    }

    private @NonNull Set<String> groundingTokens(String value) {
        String normalized = TextUtils.normalizeSearchText(value);

        if (normalized.isBlank())
            return Set.of();

        return java.util.Arrays.stream(normalized.split(" "))
                .filter(token -> token.length() >= 4 || token.chars().allMatch(Character::isDigit))
                .filter(token -> !GROUNDING_STOP_WORDS.contains(token))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private boolean sameStem(@NonNull String left, @NonNull String right) {
        if (left.chars().allMatch(Character::isDigit)
                || right.chars().allMatch(Character::isDigit))
            return left.equals(right);

        int prefixLength = Math.min(5, Math.min(left.length(), right.length()));
        return prefixLength >= 4 && left.regionMatches(0, right, 0, prefixLength);
    }

    private @NonNull String safe(String value) {
        return value == null ? "" : value;
    }

    private void validateVerbatimOverlap(@NonNull ConversationGenerationRequest request, @NonNull String answer) {
        Set<String> answerSequences = sequences(answer);

        boolean copied = request.evidence()
                .documentPassages()
                .stream()
                .map(passage -> passage.excerpt())
                .flatMap(excerpt -> sequences(excerpt).stream())
                .anyMatch(answerSequences::contains);

        if (copied)
            throw unavailable("Generated answer reproduces excessive source text");
    }

    private @NonNull Set<String> sequences(String value) {
        String normalized = TextUtils.normalizeSearchText(value);

        if (normalized.isBlank())
            return Set.of();

        String[] words = normalized.split(" ");

        if (words.length < MAXIMUM_VERBATIM_WORDS)
            return Set.of();

        Set<String> sequences = new HashSet<>();

        for (int index = 0;
             index <= words.length - MAXIMUM_VERBATIM_WORDS;
             index++) {
            sequences.add(String.join(
                    " ",
                    List.of(words).subList(
                            index,
                            index + MAXIMUM_VERBATIM_WORDS
                    )
            ));
        }

        return Set.copyOf(sequences);
    }

    @Contract("_ -> new")
    private @NonNull ConversationGenerationRejectedException unavailable(String message) {
        return new ConversationGenerationRejectedException(message);
    }
}
