package fmi.ethnowear.application.exception;

public class InvalidAdminCredentialsException extends RuntimeException {

    public InvalidAdminCredentialsException() {
        super("Invalid administrator credentials");
    }
}
