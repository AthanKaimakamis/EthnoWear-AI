package fmi.ethnowear.application.exception;

public class WorkerOcrResultConflictException extends RuntimeException {

    public WorkerOcrResultConflictException() {
        super("The OCR job already has a different accepted result");
    }
}
