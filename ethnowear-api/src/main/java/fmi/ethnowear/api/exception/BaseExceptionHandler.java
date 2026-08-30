package fmi.ethnowear.api.exception;

import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

public abstract class BaseExceptionHandler {

    protected ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
        return error(status, message, Map.of());
    }

    protected ResponseEntity<Map<String, Object>> error(
            HttpStatus status,
            String code,
            String message
    ) {
        return error(status, message, Map.of("code", code));
    }

    protected ResponseEntity<Map<String, Object>> error(@NonNull HttpStatus status, String message, Map<String, ?> additionalFields) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        body.putAll(additionalFields);

        return ResponseEntity.status(status).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> validation(
            @NonNull MethodArgumentNotValidException ex
    ) {
        Map<String, Object> fields = validationFields(ex);

        return error(
                HttpStatus.BAD_REQUEST,
                "Validation failed",
                Map.of("fields", fields)
        );
    }

    protected Map<String, Object> validationFields(
            @NonNull MethodArgumentNotValidException ex
    ) {
        return ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        field -> field.getDefaultMessage() == null
                                ? "Invalid value"
                                : field.getDefaultMessage(),
                        (first, ignored) -> first,
                        LinkedHashMap::new
                ));
    }
}
