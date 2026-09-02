package fmi.ethnowear.api.exception;

import fmi.ethnowear.application.exception.ConversationException;
import org.jspecify.annotations.NonNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestControllerAdvice(basePackages = "fmi.ethnowear.api.controller.conversation")
public class ConversationExceptionHandler extends BaseExceptionHandler {

    @ExceptionHandler(ConversationException.class)
    public ResponseEntity<Map<String, Object>> conversation(
            @NonNull ConversationException exception
    ) {
        return error(
                exception.getStatus(),
                exception.getCode(),
                exception.getMessage()
        );
    }
}
