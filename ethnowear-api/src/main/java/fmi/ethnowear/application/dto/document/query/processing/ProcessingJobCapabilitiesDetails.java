package fmi.ethnowear.application.dto.document.query.processing;

public record ProcessingJobCapabilitiesDetails(
        boolean retryable,
        boolean cloneable,
        boolean cancellable,
        boolean deletable
) {
}
