package fmi.ethnowear.application.exception;

public class UnprocessableDocumentEvidenceException extends IllegalArgumentException {

    public UnprocessableDocumentEvidenceException(String message) {
        super(message);
    }

    public UnprocessableDocumentEvidenceException(String message, Throwable cause) {
        super(message, cause);
    }
}