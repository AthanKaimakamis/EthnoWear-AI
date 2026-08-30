package fmi.ethnowear.application.dto.document.query.processing;

public record ProcessingJobProgressDetails(
        int attemptCount,
        int maxAttempts,
        int attemptsRemaining,
        boolean retryable,
        boolean cancellable,
        boolean terminal
) {
}
