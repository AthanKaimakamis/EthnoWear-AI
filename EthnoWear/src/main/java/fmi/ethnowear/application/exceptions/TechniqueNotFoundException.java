package fmi.ethnowear.application.exceptions;

public class TechniqueNotFoundException extends RuntimeException {

    public TechniqueNotFoundException(String localName) {
        super("Technique not found: " + localName);
    }
}
