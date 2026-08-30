package fmi.ethnowear.domain.model.document.processing;

public enum JobStatus {
    QUEUED,
    CLAIMED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    RETRY_WAIT,
    CANCEL_REQUESTED,
    CANCELLED,
    TIMED_OUT,
    DEAD
}
