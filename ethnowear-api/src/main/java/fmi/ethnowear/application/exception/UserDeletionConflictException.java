package fmi.ethnowear.application.exception;

public class UserDeletionConflictException extends RuntimeException {

    private final String code;

    public UserDeletionConflictException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
