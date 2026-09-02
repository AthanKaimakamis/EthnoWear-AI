package fmi.ethnowear.application.exception;

public final class ConversationTurnCancelledException extends RuntimeException {

    public ConversationTurnCancelledException() {
        super("Conversation turn was cancelled", null, false, false);
    }
}