package fmi.ethnowear.api.exception;

import fmi.ethnowear.application.exception.*;
import org.jspecify.annotations.NonNull;
import org.springframework.http.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestControllerAdvice(basePackages = {
        "fmi.ethnowear.api.controller.auth",
        "fmi.ethnowear.api.controller.user"
})
public class AdminAuthExceptionHandler extends BaseExceptionHandler {

    @ExceptionHandler(InvalidAdminCredentialsException.class)
    public ResponseEntity<Map<String, Object>> invalidCredentials(
            @NonNull InvalidAdminCredentialsException ex
    ) {
        return error(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> notFound(
            @NonNull ResourceNotFoundException ex
    ) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler({
            InvalidCurrentPasswordException.class,
            PasswordPolicyException.class,
            IllegalArgumentException.class
    })
    public ResponseEntity<Map<String, Object>> invalidInput(
            @NonNull RuntimeException ex
    ) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> conflict(
            @NonNull IllegalStateException ex
    ) {
        return error(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(UserDeletionConflictException.class)
    public ResponseEntity<Map<String, Object>> deletionConflict(
            @NonNull UserDeletionConflictException ex
    ) {
        return error(HttpStatus.CONFLICT, ex.getCode(), ex.getMessage());
    }
}
