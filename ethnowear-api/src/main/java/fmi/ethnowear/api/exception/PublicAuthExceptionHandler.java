package fmi.ethnowear.api.exception;

import fmi.ethnowear.api.controller.publicauth.PublicAuthController;
import fmi.ethnowear.application.exception.PublicAuthException;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestControllerAdvice(assignableTypes = PublicAuthController.class)
public class PublicAuthExceptionHandler extends BaseExceptionHandler {

    @ExceptionHandler(PublicAuthException.class)
    public ResponseEntity<Map<String, Object>> authentication(@NonNull PublicAuthException ex) {
        return error(ex.getStatus(), ex.getCode(), ex.getMessage());
    }

    @Override
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> validation(MethodArgumentNotValidException ex) {
        return error(HttpStatus.BAD_REQUEST, "Validation failed",
                Map.of("code", "PUBLIC_AUTH_VALIDATION_FAILED", "fields", validationFields(ex)));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> malformed() {
        return error(HttpStatus.BAD_REQUEST, "PUBLIC_AUTH_VALIDATION_FAILED", "Invalid login input");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> unexpected(@NonNull Exception ex) {
        // Do not log an exception message that may contain a submitted credential or profile.
        log.error("Public authentication failed: {}", ex.getClass().getSimpleName());
        return error(HttpStatus.SERVICE_UNAVAILABLE, "PUBLIC_AUTH_UNAVAILABLE", "Public sign-in is temporarily unavailable");
    }
}
