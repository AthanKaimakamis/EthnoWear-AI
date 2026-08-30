package fmi.ethnowear.application.service.worker.job;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fmi.ethnowear.application.dto.worker.completion.WorkerJobCompletionDetails;
import fmi.ethnowear.application.dto.worker.indexing.WorkerIndexResultCommand;
import fmi.ethnowear.application.dto.worker.indexing.WorkerIndexResultDetails;
import fmi.ethnowear.application.dto.worker.indexing.WorkerIndexingContextDetails;
import fmi.ethnowear.application.dto.worker.job.WorkerJobType;
import fmi.ethnowear.application.exception.UnprocessableDocumentEvidenceException;
import fmi.ethnowear.application.exception.WorkerClaimConflictException;
import fmi.ethnowear.application.exception.WorkerIndexResultConflictException;
import fmi.ethnowear.application.exception.WorkerManifestConflictException;
import fmi.ethnowear.application.exception.WorkerPayloadTooLargeException;
import fmi.ethnowear.application.model.event.ManagementEvent;
import fmi.ethnowear.application.model.worker.WorkerClaimCredentials;
import fmi.ethnowear.application.service.event.ManagementEventPublisher;
import fmi.ethnowear.application.service.document.indexing.DocumentIndexingStateReconciler;
import fmi.ethnowear.application.service.worker.security.WorkerClaimedJobLoader;
import fmi.ethnowear.config.WorkerIndexingProperties;
import fmi.ethnowear.domain.model.archive.KnowledgeChunkType;
import fmi.ethnowear.domain.model.document.DocumentType;
import fmi.ethnowear.domain.model.document.EvidenceState;
import fmi.ethnowear.domain.model.document.indexing.IndexingState;
import fmi.ethnowear.domain.model.document.processing.JobStatus;
import fmi.ethnowear.domain.model.document.processing.JobType;
import fmi.ethnowear.domain.model.document.processing.ProcessingState;
import fmi.ethnowear.domain.model.document.provenance.ProvenanceTrustState;
import fmi.ethnowear.domain.model.document.review.ReviewState;
import fmi.ethnowear.domain.model.document.review.TranscriptionApprovalState;
import fmi.ethnowear.persistence.jpa.entity.KnowledgeChunk;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPage;
import fmi.ethnowear.persistence.jpa.entity.document.Document;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentProcessingJob;
import fmi.ethnowear.persistence.jpa.entity.document.KnowledgeChunkPage;
import fmi.ethnowear.persistence.jpa.repository.KnowledgeChunkRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentProcessingJobRepository;
import fmi.ethnowear.persistence.jpa.repository.document.KnowledgeChunkPageRepository;
import fmi.ethnowear.util.ContentHashUtils;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static fmi.ethnowear.util.TextUtils.isBlank;

@Service
@RequiredArgsConstructor
public class WorkerIndexingService {

    private static final Set<IndexingState> ELIGIBLE_INDEXING_STATES = Set.of(
            IndexingState.PENDING,
            IndexingState.FAILED,
            IndexingState.OUTDATED
    );

    private final WorkerClaimedJobLoader jobLoader;
    private final DocumentProcessingJobRepository jobRepository;
    private final KnowledgeChunkRepository chunkRepository;
    private final KnowledgeChunkPageRepository chunkPageRepository;
    private final WorkerIndexingProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final ManagementEventPublisher managementEvents;
    private final DocumentIndexingStateReconciler indexingStateReconciler;

    @Transactional
    public WorkerIndexingContextDetails context(
            Long jobId,
            WorkerClaimCredentials credentials
    ) {
        DocumentProcessingJob job = requireActiveIndexingJob(jobId, credentials);
        KnowledgeChunk chunk = requireChunk(job);
        validateEligible(chunk);

        if (chunk.getContent().length() > properties.maximumContentCharacters())
            throw new WorkerPayloadTooLargeException(
                    "Knowledge chunk content exceeds the indexing limit"
            );

        return new WorkerIndexingContextDetails(
                job.getId(),
                chunk.getId(),
                chunk.getContent(),
                chunk.getContentHash(),
                chunk.getLanguage(),
                chunk.getChunkType(),
                chunk.getDocument() == null ? null : chunk.getDocument().getId(),
                chunk.getSourceReference() == null
                        ? null
                        : chunk.getSourceReference().getId(),
                chunk.getArchiveItem() == null ? null : chunk.getArchiveItem().getId(),
                chunk.getOntologyIri(),
                chunk.getProvenanceTrustState(),
                chunk.getTranscriptionApprovalState()
        );
    }

    @Transactional
    public WorkerIndexResultDetails accept(
            Long jobId,
            WorkerClaimCredentials credentials,
            WorkerIndexResultCommand command
    ) {
        DocumentProcessingJob job = requireActiveIndexingJob(jobId, credentials);
        KnowledgeChunk chunk = requireChunk(job);
        validateEligible(chunk);

        if (!isBlank(job.getParametersJson())) {
            WorkerIndexResultCommand existing = acceptedResult(job);

            if (!existing.equals(command))
                throw new WorkerIndexResultConflictException();

            return details(job, chunk, true);
        }

        WorkerIndexResultCommand validated = validateResult(chunk, command);
        job.setParametersJson(writeResult(validated));
        jobRepository.saveAndFlush(job);
        return details(job, chunk, false);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public WorkerJobCompletionDetails complete(
            @NonNull DocumentProcessingJob job,
            WorkerClaimCredentials credentials
    ) {
        Document document = lockTargetDocument(job);
        KnowledgeChunk chunk = requireChunk(job);

        if (job.getStatus() == JobStatus.SUCCEEDED) {
            reconcile(document);
            return existingCompletion(job, chunk);
        }

        jobLoader.validateActive(job, credentials);

        if (job.getStatus() == JobStatus.CANCEL_REQUESTED)
            throw new WorkerClaimConflictException();

        validateEligible(chunk);
        WorkerIndexResultCommand accepted = acceptedResult(job);
        validateResult(chunk, accepted);

        LocalDateTime indexedAt = LocalDateTime.ofInstant(
                clock.instant(),
                ZoneOffset.UTC
        );

        chunk.setIndexingState(IndexingState.INDEXED);
        chunk.setEmbeddingModel(accepted.embeddingModel());
        chunk.setEmbeddingDimensions(accepted.embeddingDimensions());
        chunk.setVectorCollection(accepted.vectorCollection());
        chunk.setVectorPointId(accepted.vectorPointId());
        chunk.setIndexedContentHash(accepted.contentHash());
        chunk.setIndexedAt(indexedAt);
        chunk.setIndexingError(null);
        finish(job, indexedAt);

        chunkRepository.saveAndFlush(chunk);
        jobRepository.saveAndFlush(job);
        reconcile(document);
        publishCompleted(job);

        return completion(job, chunk, false);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordFailure(
            @NonNull DocumentProcessingJob job,
            String safeMessage
    ) {
        if (job.getJobType() != JobType.INDEX_CHUNK
                || job.getKnowledgeChunk() == null)
            return;

        Document document = lockTargetDocument(job);
        KnowledgeChunk chunk = requireChunk(job);
        chunk.setIndexingState(IndexingState.FAILED);
        chunk.setIndexingError(safeMessage);
        chunkRepository.saveAndFlush(chunk);
        reconcile(document);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordCancellation(@NonNull DocumentProcessingJob job) {
        recordFailure(job, "Indexing was cancelled");
    }

    private Document lockTargetDocument(DocumentProcessingJob job) {
        Document document = job.getDocument();

        if (document == null && job.getKnowledgeChunk() != null)
            document = job.getKnowledgeChunk().getDocument();

        return document == null
                ? null
                : indexingStateReconciler.lockDocument(document.getId());
    }

    private void reconcile(Document document) {
        if (document != null)
            indexingStateReconciler.reconcileLocked(document);
    }

    private DocumentProcessingJob requireActiveIndexingJob(
            Long jobId,
            WorkerClaimCredentials credentials
    ) {
        DocumentProcessingJob job = jobLoader.requireActive(jobId, credentials);

        if (job.getStatus() == JobStatus.CANCEL_REQUESTED)
            throw new WorkerClaimConflictException();

        if (job.getJobType() != JobType.INDEX_CHUNK)
            throw new IllegalArgumentException("Job is not an INDEX_CHUNK job");

        return job;
    }

    private @NonNull KnowledgeChunk requireChunk(
            @NonNull DocumentProcessingJob job
    ) {
        if (job.getJobType() != JobType.INDEX_CHUNK
                || job.getKnowledgeChunk() == null
                || job.getKnowledgeChunk().getId() == null)
            throw new WorkerManifestConflictException(
                    "INDEX_CHUNK job has no knowledge chunk target"
            );

        KnowledgeChunk chunk = chunkRepository
                .findByIdForUpdate(job.getKnowledgeChunk().getId())
                .orElseThrow(() -> new WorkerManifestConflictException(
                        "INDEX_CHUNK target no longer exists"
                ));

        if (!Objects.equals(chunk.getId(), job.getKnowledgeChunk().getId()))
            throw new WorkerManifestConflictException(
                    "INDEX_CHUNK target does not match the claimed job"
            );

        return chunk;
    }

    private void validateEligible(@NonNull KnowledgeChunk chunk) {
        if (chunk.getSupersededBy() != null)
            throw unusable("Knowledge chunk has been superseded");

        if (!ELIGIBLE_INDEXING_STATES.contains(chunk.getIndexingState()))
            throw unusable("Knowledge chunk is not pending indexing");

        if (chunk.getTranscriptionApprovalState()
                != TranscriptionApprovalState.APPROVED)
            throw unusable("Knowledge chunk transcription is not approved");

        if (!provenanceEligible(chunk))
            throw unusable("Knowledge chunk provenance is not eligible");

        if (isBlank(chunk.getContent()))
            throw unusable("Knowledge chunk content is blank");

        if (!ContentHashUtils.sha256(chunk.getContent())
                .equals(chunk.getContentHash()))
            throw unusable("Knowledge chunk content hash is invalid");

        List<KnowledgeChunkPage> pageLinks = chunkPageRepository
                .findByKnowledgeChunk_IdOrderByPageOrderAsc(chunk.getId());
        pageLinks.forEach(link -> validatePage(chunk, link));
    }

    private void validatePage(
            KnowledgeChunk chunk,
            KnowledgeChunkPage link
    ) {
        DocumentPage page = link.getDocumentPage();

        if (page == null
                || page.getEvidenceState() != EvidenceState.ACTIVE
                || page.getProcessingState() != ProcessingState.COMPLETED
                || page.getReviewState() != ReviewState.APPROVED
                || page.getTranscriptionApprovalState()
                != TranscriptionApprovalState.APPROVED
                || page.getIndexingState() == IndexingState.NOT_ELIGIBLE
                || isBlank(page.getCorrectedText())
                || !ContentHashUtils.sha256(page.getCorrectedText())
                .equals(page.getCorrectedTextHash())
                || !pageProvenanceEligible(chunk, page))
            throw unusable("A cited document page is no longer eligible for indexing");
    }

    private boolean provenanceEligible(KnowledgeChunk chunk) {
        if (chunk.getChunkType() == KnowledgeChunkType.STANDALONE_EVIDENCE)
            return chunk.getProvenanceTrustState()
                    != ProvenanceTrustState.UNTRUSTED;

        return chunk.getSourceReference() != null
                && trusted(chunk.getProvenanceTrustState());
    }

    private boolean pageProvenanceEligible(
            KnowledgeChunk chunk,
            DocumentPage page
    ) {
        DocumentType documentType = chunk.getDocument() == null
                ? null
                : chunk.getDocument().getDocumentType();

        if (documentType == DocumentType.STANDALONE_CAPTURE
                || documentType == DocumentType.UNKNOWN_FRAGMENT_SET)
            return page.getProvenanceTrustState()
                    != ProvenanceTrustState.UNTRUSTED;

        return page.getSourceReference() != null
                && trusted(page.getProvenanceTrustState());
    }

    private boolean trusted(ProvenanceTrustState state) {
        return state == ProvenanceTrustState.TRUSTED
                || state == ProvenanceTrustState.VERIFIED;
    }

    private WorkerIndexResultCommand validateResult(
            @NonNull KnowledgeChunk chunk,
            WorkerIndexResultCommand command
    ) {
        if (command == null)
            throw new IllegalArgumentException("Index result is required");

        requireBounded(command.embeddingModel(), properties.maximumEmbeddingModelCharacters(), "Embedding model");
        requireBounded(command.vectorCollection(), properties.maximumVectorCollectionCharacters(), "Vector collection");
        requireBounded(command.vectorPointId(), properties.maximumVectorPointIdCharacters(), "Vector point id");

        if (command.embeddingDimensions() <= 0
                || command.embeddingDimensions() > properties.maximumEmbeddingDimensions())
            throw new IllegalArgumentException("Embedding dimensions are invalid");

        if (!Objects.equals(command.contentHash(), chunk.getContentHash())
                || !Objects.equals(
                command.contentHash(),
                ContentHashUtils.sha256(chunk.getContent())
        ))
            throw new WorkerManifestConflictException(
                    "Index result content hash is stale"
            );

        if (!properties.expectedEmbeddingModel().equals(command.embeddingModel())
                || properties.expectedEmbeddingDimensions()
                != command.embeddingDimensions()
                || !properties.expectedVectorCollection()
                .equals(command.vectorCollection()))
            throw new IllegalArgumentException(
                    "Index result does not match the configured embedding contract"
            );

        if (!String.valueOf(chunk.getId()).equals(command.vectorPointId()))
            throw new IllegalArgumentException(
                    "Vector point id must equal the knowledge chunk id"
            );

        return command;
    }

    private void requireBounded(String value, int maximum, String field) {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException(field + " is required");

        if (value.length() > maximum)
            throw new IllegalArgumentException(
                    field + " exceeds the configured limit"
            );
    }

    private String writeResult(WorkerIndexResultCommand command) {
        try {
            return objectMapper.writeValueAsString(command);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Index result could not be persisted", ex);
        }
    }

    private WorkerIndexResultCommand acceptedResult(
            @NonNull DocumentProcessingJob job
    ) {
        if (isBlank(job.getParametersJson()))
            throw new UnprocessableDocumentEvidenceException(
                    "INDEX_CHUNK job has no accepted index result"
            );

        try {
            return objectMapper.readValue(
                    job.getParametersJson(),
                    WorkerIndexResultCommand.class
            );
        } catch (JsonProcessingException ex) {
            throw new WorkerManifestConflictException(
                    "INDEX_CHUNK job has invalid accepted-result metadata"
            );
        }
    }

    private void finish(
            @NonNull DocumentProcessingJob job,
            LocalDateTime now
    ) {
        job.setStatus(JobStatus.SUCCEEDED);
        job.setFinishedAt(now);
        job.setErrorCode(null);
        job.setSafeErrorMessage(null);
        job.setErrorDetailsJson(null);
        job.setCancellationReason(null);
        job.clearClaimOwnership();
        job.clearActiveJobKey();
    }

    private WorkerJobCompletionDetails existingCompletion(
            @NonNull DocumentProcessingJob job,
            @NonNull KnowledgeChunk chunk
    ) {
        WorkerIndexResultCommand result = acceptedResult(job);

        if (chunk.getIndexingState() != IndexingState.INDEXED
                || !Objects.equals(chunk.getEmbeddingModel(), result.embeddingModel())
                || !Objects.equals(chunk.getEmbeddingDimensions(), result.embeddingDimensions())
                || !Objects.equals(chunk.getVectorCollection(), result.vectorCollection())
                || !Objects.equals(chunk.getVectorPointId(), result.vectorPointId())
                || !Objects.equals(chunk.getIndexedContentHash(), result.contentHash())
                || chunk.getIndexedAt() == null)
            throw new WorkerManifestConflictException(
                    "Completed INDEX_CHUNK job has inconsistent vector metadata"
            );

        return completion(job, chunk, true);
    }

    private WorkerJobCompletionDetails completion(
            DocumentProcessingJob job,
            KnowledgeChunk chunk,
            boolean existing
    ) {
        return new WorkerJobCompletionDetails(
                job.getId(),
                WorkerJobType.INDEX_CHUNK,
                chunk.getDocument() == null ? null : chunk.getDocument().getId(),
                null,
                0,
                0,
                0,
                existing
        );
    }

    private WorkerIndexResultDetails details(
            DocumentProcessingJob job,
            KnowledgeChunk chunk,
            boolean existing
    ) {
        return new WorkerIndexResultDetails(
                job.getId(),
                chunk.getId(),
                existing
        );
    }

    private void publishCompleted(DocumentProcessingJob job) {
        if (job.getDocument() != null)
            managementEvents.document(
                    job.getDocument(),
                    ManagementEvent.Action.STATUS_CHANGED
            );

        managementEvents.processingJob(
                job,
                ManagementEvent.Action.STATUS_CHANGED
        );
    }

    private UnprocessableDocumentEvidenceException unusable(String message) {
        return new UnprocessableDocumentEvidenceException(message);
    }
}
