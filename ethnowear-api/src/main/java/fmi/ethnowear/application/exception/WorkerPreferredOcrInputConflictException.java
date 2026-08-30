package fmi.ethnowear.application.exception;

public class WorkerPreferredOcrInputConflictException extends RuntimeException {

    public WorkerPreferredOcrInputConflictException() {
        super("The preferred OCR rendition changed concurrently; retry completion");
    }
}
