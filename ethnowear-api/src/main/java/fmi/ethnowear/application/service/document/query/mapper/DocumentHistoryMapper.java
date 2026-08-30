package fmi.ethnowear.application.service.document.query.mapper;

import fmi.ethnowear.application.dto.document.query.history.*;
import fmi.ethnowear.persistence.jpa.entity.AppendOnlyEntity;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPageOcrResult;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPageProvenanceEvent;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPageReview;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentProcessingJob;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.function.Function;

@Component
public class DocumentHistoryMapper {

    private final fmi.ethnowear.application.service.document.processing.ProcessingJobCapabilitiesPolicy
            capabilitiesPolicy = new fmi.ethnowear.application.service.document.processing.ProcessingJobCapabilitiesPolicy();

    public DocumentPageOcrResultDetails toDetails(DocumentPageOcrResult result) {
        return new DocumentPageOcrResultDetails(
                result.getId(),
                id(result.getDocumentPage()),
                id(result.getDocumentPageMedia()),
                id(result.getProcessingJob()),
                result.getRawText(),
                result.getOcrEngine(),
                result.getOcrEngineVersion(),
                result.getOcrLanguage(),
                result.getOcrConfidence(),
                result.isCurrent(),
                result.getCreatedAt(),
                result.getUpdatedAt()
        );
    }

    public DocumentPageReviewDetails toDetails(DocumentPageReview review) {
        return new DocumentPageReviewDetails(
                review.getId(),
                id(review.getDocumentPage()),
                id(review.getSourceTextSuggestion()),
                review.getSourceTextSuggestionIssueOrdinal(),
                review.getReviewAction(),
                review.getReviewer(),
                review.getCorrectedTextSnapshot(),
                review.getPreviousReviewState(),
                review.getNewReviewState(),
                review.getPreviousApprovalState(),
                review.getNewApprovalState(),
                review.getReason(),
                review.getCreatedAt()
        );
    }

    public DocumentPageProvenanceEventDetails toDetails(DocumentPageProvenanceEvent event) {
        return new DocumentPageProvenanceEventDetails(
                event.getId(),
                id(event.getDocumentPage()),
                event.getEventType(),
                id(event.getPreviousSourceReference()),
                id(event.getNewSourceReference()),
                event.getPreviousProvenanceStatus(),
                event.getNewProvenanceStatus(),
                event.getPreviousTrustState(),
                event.getNewTrustState(),
                id(event.getPreviousCanonicalDocumentPage()),
                id(event.getNewCanonicalDocumentPage()),
                event.getReviewedBy(),
                event.getReason(),
                event.getCreatedAt()
        );
    }

    public DocumentProcessingJobDetails toDetails(DocumentProcessingJob job) {
        return new DocumentProcessingJobDetails(
                job.getId(),
                id(job.getPreviousJob()),
                job.getJobType(),
                job.getStatus(),
                id(job.getDocument()),
                id(job.getDocumentPage()),
                id(job.getInputMediaAsset()),
                id(job.getKnowledgeChunk()),
                job.getPriority(),
                job.getAttemptCount(),
                job.getMaxAttempts(),
                job.getAvailableAt(),
                job.getClaimedAt(),
                job.getStartedAt(),
                job.getFinishedAt(),
                job.getTimeoutAt(),
                job.getProcessorName(),
                job.getProcessorVersion(),
                job.getToolName(),
                job.getToolVersion(),
                job.getErrorCode(),
                job.getSafeErrorMessage(),
                job.getCancellationReason(),
                job.getCreatedAt(),
                job.getUpdatedAt(),
                capabilitiesPolicy.capabilities(job),
                new fmi.ethnowear.application.dto.document.query.processing.ProcessingJobRetirementDetails(
                        job.getRetiredAt() != null,
                        job.getRetiredAt(),
                        job.getRetiredBy(),
                        job.getRetirementReason()
                ),
                fmi.ethnowear.util.RowVersionUtils.token(job.getRowVersion())
        );
    }

    public BoundedHistoryDetails<DocumentProcessingJobDetails>
    toBoundedJobHistory(Page<DocumentProcessingJob> jobs) {
        return toBounded(jobs, this::toDetails);
    }

    private <E, D> BoundedHistoryDetails<D> toBounded(
            Page<E> page,
            Function<E, D> mapper
    ) {
        return new BoundedHistoryDetails<>(
                page.getContent()
                        .stream()
                        .map(mapper)
                        .toList(),
                page.hasNext()
        );
    }

    private Long id(AppendOnlyEntity entity) {
        return entity == null ? null : entity.getId();
    }
}
