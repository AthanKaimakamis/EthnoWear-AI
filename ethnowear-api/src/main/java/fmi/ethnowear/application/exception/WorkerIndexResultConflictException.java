package fmi.ethnowear.application.exception;

public class WorkerIndexResultConflictException extends RuntimeException {

    public WorkerIndexResultConflictException() {
        super("A different indexing result has already been accepted for this job");
    }
}
