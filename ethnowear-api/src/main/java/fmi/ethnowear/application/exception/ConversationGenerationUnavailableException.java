package fmi.ethnowear.application.exception;

public class ConversationGenerationUnavailableException extends RuntimeException {

    public ConversationGenerationUnavailableException(String message) {
        super(message);
    }

    public ConversationGenerationUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}