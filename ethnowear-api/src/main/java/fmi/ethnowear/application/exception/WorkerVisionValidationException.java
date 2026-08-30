package fmi.ethnowear.application.exception;

import lombok.Getter;

@Getter
public class WorkerVisionValidationException extends IllegalArgumentException {

    private final String code;

    public WorkerVisionValidationException(String code, String message) {
        super(message);
        this.code = code;
    }
}
