package fmi.ethnowear.domain.model.ontology;

import java.util.Locale;

import static fmi.ethnowear.util.TextUtils.isBlank;

public enum OntologyLanguage {
    BG("bg"),
    EN("en");

    private final String tag;

    OntologyLanguage(String tag) {
        this.tag = tag;
    }

    public String tag() {
        return tag;
    }

    public static OntologyLanguage fromTag(String tag) {
        if (isBlank(tag))
            return BG;

        return switch (tag.trim().toLowerCase(Locale.ROOT)) {
            case "bg" -> BG;
            case "en" -> EN;
            default -> throw new IllegalArgumentException("Unsupported language: " + tag);
        };
    }
}
