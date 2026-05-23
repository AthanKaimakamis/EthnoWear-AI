package fmi.ethnowear.ontology;

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
}
