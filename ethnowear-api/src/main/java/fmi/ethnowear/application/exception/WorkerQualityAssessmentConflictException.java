package fmi.ethnowear.application.exception;

public class WorkerQualityAssessmentConflictException extends RuntimeException {

    public WorkerQualityAssessmentConflictException() {
        super("A different quality assessment was already accepted for this job");
    }
}
