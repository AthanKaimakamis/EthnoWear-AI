package fmi.ethnowear.application.dto.worker;

public record WorkerApiError(
        int status,
        String error,
        String code,
        String message
) {
}