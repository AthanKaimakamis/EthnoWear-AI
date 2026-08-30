package fmi.ethnowear.application.exception;

public class WorkerNotReadyException extends RuntimeException {

    public WorkerNotReadyException(String message) {
        super(message);
    }

    public WorkerNotReadyException(String message, Throwable cause) {
        super(message, cause);
    }
}
