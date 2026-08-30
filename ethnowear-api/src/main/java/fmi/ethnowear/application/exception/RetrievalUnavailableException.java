package fmi.ethnowear.application.exception;

public class RetrievalUnavailableException extends RuntimeException {

    public RetrievalUnavailableException(String message) {
        super(message);
    }

    public RetrievalUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}