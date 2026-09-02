package fmi.ethnowear.application.exception;

public class ConversationGenerationRejectedException extends RuntimeException {

    public ConversationGenerationRejectedException(String message) {
        super(message);
    }

    public ConversationGenerationRejectedException(String message, Throwable cause) {
        super(message, cause);
    }
}
