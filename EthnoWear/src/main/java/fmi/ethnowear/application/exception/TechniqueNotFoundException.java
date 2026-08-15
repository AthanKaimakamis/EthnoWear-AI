package fmi.ethnowear.application.exception;

public class TechniqueNotFoundException extends RuntimeException {

    public TechniqueNotFoundException(String localName) {
        super("Technique not found: " + localName);
    }
}
