package fmi.ethnowear.application.service.conversation.generation;

import fmi.ethnowear.application.model.conversation.ConversationEvidenceBundle;
import fmi.ethnowear.domain.model.ontology.FeatureType;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Complete structured lookup results, separate from the model's narrative.
 * Each list retains its owning entity; a region's links never become a style's links.
 */
final class ConversationRelationshipSummary {
    private static final Pattern LIST_REQUEST = Pattern.compile(
            "(?iuU)\\b(кои|кой|какви|изброй|изброи|покажи|which|what|list|show)\\b");

    private ConversationRelationshipSummary() {}

    static String forQuestion(String question, String language, ConversationEvidenceBundle evidence, String narrative) {
        if (!LIST_REQUEST.matcher(question).find()) return "";
        String normalized = question.toLowerCase(Locale.ROOT);
        Set<FeatureType> types = new LinkedHashSet<>();
        if (contains(normalized, "техники", "бодове", "techniques", "stitches")) types.add(FeatureType.TECHNIQUE);
        if (contains(normalized, "орнаменти", "ornaments")) types.add(FeatureType.ORNAMENT);
        if (contains(normalized, "мотиви", "motifs")) types.add(FeatureType.MOTIF);
        if (contains(normalized, "цветове", "colors", "colours")) types.add(FeatureType.COLOR);
        StringBuilder result = new StringBuilder();
        boolean english = "en".equals(language);
        for (var owner : evidence.ontologyEvidence()) {
            for (FeatureType type : types) {
                var labels = owner.relationships().stream()
                        .filter(link -> link.startsWith(type + ": "))
                        .map(link -> link.substring(type.name().length() + 2))
                        .map(link -> link.replaceFirst(" \\[[^\\]]+\\]$", ""))
                        .distinct().toList();
                if (labels.isEmpty()) continue;
                String normalizedNarrative = narrative.toLowerCase(Locale.ROOT);
                if (normalizedNarrative.contains(owner.label().toLowerCase(Locale.ROOT))
                        && labels.stream().allMatch(label -> normalizedNarrative.contains(label.toLowerCase(Locale.ROOT)))) continue;
                result.append("\n\n").append(english ? "Registered relationships for " : "Записани връзки за ")
                        .append(owner.label()).append(" — ").append(label(type, english))
                        .append(" (").append(labels.size()).append("):\n• ")
                        .append(String.join("\n• ", labels));
            }
        }
        return result.toString();
    }

    private static boolean contains(String text, String... words) {
        for (String word : words) if (Pattern.compile("(?iuU)\\b" + word + "\\b").matcher(text).find()) return true;
        return false;
    }

    private static String label(FeatureType type, boolean english) {
        return switch (type) {
            case TECHNIQUE -> english ? "techniques" : "техники";
            case ORNAMENT -> english ? "ornaments" : "орнаменти";
            case MOTIF -> english ? "motifs" : "мотиви";
            case COLOR -> english ? "colors" : "цветове";
            default -> throw new IllegalArgumentException("Unsupported relationship list type");
        };
    }
}
