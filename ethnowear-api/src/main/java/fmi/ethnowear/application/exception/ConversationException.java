package fmi.ethnowear.application.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class ConversationException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ConversationException(
            HttpStatus status,
            String code,
            String message
    ) {
        super(message);
        this.status = status;
        this.code = code;
    }
}