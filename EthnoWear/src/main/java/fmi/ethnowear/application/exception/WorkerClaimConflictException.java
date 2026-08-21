package fmi.ethnowear.application.exception;

public class WorkerClaimConflictException extends RuntimeException {

    public WorkerClaimConflictException() {
        super("The worker claim is stale or does not own this job");
    }
}