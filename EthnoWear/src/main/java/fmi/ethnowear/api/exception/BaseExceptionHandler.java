package fmi.ethnowear.api.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.LinkedHashMap;
import java.util.Map;

public abstract class BaseExceptionHandler {

    protected ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
        return error(status, message, Map.of());
    }

    protected ResponseEntity<Map<String, Object>> error(HttpStatus status, String message, Map<String, ?> additionalFields) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        body.putAll(additionalFields);

        return ResponseEntity.status(status).body(body);
    }
}
