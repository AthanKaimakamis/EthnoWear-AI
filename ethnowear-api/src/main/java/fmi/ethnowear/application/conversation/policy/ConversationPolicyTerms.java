package fmi.ethnowear.application.conversation.policy;

import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import fmi.ethnowear.domain.model.conversation.ConversationArchiveTarget;

public final class ConversationPolicyTerms {

    public static final List<String> FORBIDDEN_PHRASES = List.of(
            "ignore previous instructions",
            "ignore system instructions",
            "reveal system prompt",
            "show system prompt",
            "developer message",
            "show credentials",
            "execute command",
            "run command",
            "read arbitrary file",
            "run sql",
            "drop table",
            "игнорирай предишните инструкции",
            "игнорирай системните инструкции",
            "покажи системния промпт",
            "разкрий системния промпт",
            "покажи паролата",
            "покажи тайните",
            "изпълни команда",
            "прочети произволен файл",
            "изпълни sql",
            "изтрий таблицата"
    );

    public static final List<String> DOMAIN_TERM_PREFIXES = List.of(
            "шевиц",
            "бродер",
            "орнамент",
            "мотив",
            "техник",
            "бод",
            "нос",
            "регион",
            "елхов",
            "дедеага",
            "етнограф",
            "фолклор",
            "традицион",
            "престилк",
            "сукман",
            "везб",
            "embroid",
            "ornament",
            "motif",
            "stitch",
            "technique",
            "costume",
            "region",
            "ethnograph",
            "folklore",
            "traditional"
    );

    public static final List<String> FOLLOW_UP_PHRASES = List.of(
            "това",
            "тази",
            "този",
            "тези",
            "него",
            "нея",
            "същият",
            "същата",
            "а как",
            "а кои",
            "а коя",
            "а къде",
            "покажи още",
            "this",
            "that",
            "these",
            "those",
            "it",
            "they",
            "them",
            "what about",
            "show more"
    );

    public static final List<String> GREETINGS = List.of(
            "здравей",
            "здравейте",
            "добър ден",
            "hello",
            "hi"
    );

    public static final List<String> ARCHIVE_DISCOVERY_TERM_PREFIXES = List.of(
            "покаж",
            "отвори",
            "разглед",
            "намер",
            "виж",
            "show",
            "open",
            "browse",
            "find",
            "view",
            "list"
    );

    public static final Map<ConversationArchiveTarget, List<String>> ARCHIVE_TARGET_TERMS = Map.of(
            ConversationArchiveTarget.REGIONAL_EMBROIDERY,
            List.of("регионална шевица", "регионални шевици", "regional embroidery", "regional embroideries"),
            ConversationArchiveTarget.REGIONAL_MOTIF,
            List.of("регионален мотив", "регионални мотиви", "regional motif", "regional motifs"),
            ConversationArchiveTarget.MOTIF,
            List.of("мотив", "motif"),
            ConversationArchiveTarget.TECHNIQUE,
            List.of("техник", "бод", "technique", "stitch"),
            ConversationArchiveTarget.ORNAMENT,
            List.of("орнамент", "ornament")
    );

    public static final Pattern DOMAIN_TERMS =
            prefixPattern(DOMAIN_TERM_PREFIXES);

    public static final Pattern FOLLOW_UP_TERMS =
            phrasePattern(FOLLOW_UP_PHRASES);

    private ConversationPolicyTerms() {
    }

    private static @NonNull Pattern prefixPattern(@NonNull List<String> prefixes) {
        String alternatives = prefixes.stream()
                .map(Pattern::quote)
                .map(prefix -> prefix + "\\p{L}*")
                .reduce((left, right) -> left + "|" + right)
                .orElseThrow();

        return Pattern.compile(
                "\\b(" + alternatives + ")\\b",
                Pattern.CASE_INSENSITIVE
                        | Pattern.UNICODE_CASE
                        | Pattern.UNICODE_CHARACTER_CLASS
        );
    }

    private static @NonNull Pattern phrasePattern(@NonNull List<String> phrases) {
        String alternatives = phrases.stream()
                .map(Pattern::quote)
                .reduce((left, right) -> left + "|" + right)
                .orElseThrow();

        return Pattern.compile(
                "\\b(" + alternatives + ")\\b",
                Pattern.CASE_INSENSITIVE
                        | Pattern.UNICODE_CASE
                        | Pattern.UNICODE_CHARACTER_CLASS
        );
    }
}
