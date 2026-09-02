package fmi.ethnowear.application.service.worker.job;

import fmi.ethnowear.application.dto.worker.failure.WorkerJobFailureCommand;
import fmi.ethnowear.domain.model.document.processing.JobType;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentProcessingJob;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;

@Component
public class IndexingFailureClassifier {

    private static final Set<String> CONTRACT_MARKERS = Set.of(
            "CONTRACT", "INVALID_PAYLOAD", "PAYLOAD_INVALID", "SCHEMA",
            "DIMENSION_MISMATCH", "MODEL_MISMATCH", "COLLECTION_MISMATCH",
            "UNSUPPORTED_RESPONSE"
    );

    private static final Set<String> STALE_MARKERS = Set.of(
            "STALE", "HASH", "OUTDATED", "CONTENT_CHANGED"
    );

    private static final Set<String> ELIGIBILITY_MARKERS = Set.of(
            "ELIGIB", "APPROV", "PROVENANCE", "SUPERSEDED", "NOT_CURRENT", "RETIRED"
    );

    public WorkerJobFailureCommand normalize(
            @NonNull DocumentProcessingJob job,
            @NonNull WorkerJobFailureCommand command
    ) {
        if (job.getJobType() != JobType.INDEX_CHUNK)
            return command;

        String diagnostic = (command.errorCode() + " " + command.safeErrorMessage())
                .toUpperCase(Locale.ROOT);

        if (containsAny(diagnostic, STALE_MARKERS))
            return classified("INDEXING_STALE_CONTENT", "The chunk content changed before indexing completed", command);

        if (containsAny(diagnostic, ELIGIBILITY_MARKERS))
            return classified("INDEXING_ELIGIBILITY_CHANGED", "The chunk is no longer eligible for indexing", command);

        if (containsAny(diagnostic, CONTRACT_MARKERS))
            return classified("INDEXING_CONTRACT_MISMATCH", "The indexing contract is incompatible", command);

        return classified("INDEXING_INFRASTRUCTURE_FAILURE", "The indexing infrastructure is unavailable", command);
    }

    private boolean containsAny(String diagnostic, Set<String> markers) {
        return markers.stream().anyMatch(diagnostic::contains);
    }

    private WorkerJobFailureCommand classified(
            String code,
            String message,
            WorkerJobFailureCommand original
    ) {
        return new WorkerJobFailureCommand(code, message, original.retryable());
    }
}
