package fmi.ethnowear.api.exception;

import fmi.ethnowear.application.exception.RetrievalUnavailableException;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice(
        basePackages = "fmi.ethnowear.api.controller.retrieval"
)
public class RetrievalApiExceptionHandler extends BaseExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> invalidQuery(
            @NonNull IllegalArgumentException exception
    ) {
        return error(
                HttpStatus.BAD_REQUEST,
                "INVALID_RETRIEVAL_QUERY",
                exception.getMessage()
        );
    }

    @ExceptionHandler(RetrievalUnavailableException.class)
    public ResponseEntity<Map<String, Object>> unavailable(
            @NonNull RetrievalUnavailableException exception
    ) {
        return error(
                HttpStatus.SERVICE_UNAVAILABLE,
                "RETRIEVAL_UNAVAILABLE",
                exception.getMessage()
        );
    }

    @Override
    public ResponseEntity<Map<String, Object>> validation(
            @NonNull MethodArgumentNotValidException exception
    ) {
        return error(
                HttpStatus.BAD_REQUEST,
                "Validation failed",
                Map.of(
                        "code",
                        "INVALID_RETRIEVAL_QUERY",
                        "fields",
                        validationFields(exception)
                )
        );
    }
}
