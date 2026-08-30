package fmi.ethnowear.application.exception;

public class WorkerVisionAssessmentConflictException extends RuntimeException {

    public WorkerVisionAssessmentConflictException() {
        super("A different vision assessment was already accepted for this job");
    }
}
