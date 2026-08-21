package fmi.ethnowear.application.exception;

public class WorkerClaimExpiredException extends RuntimeException {

    public WorkerClaimExpiredException() {
        super("The worker claim has expired");
    }
}