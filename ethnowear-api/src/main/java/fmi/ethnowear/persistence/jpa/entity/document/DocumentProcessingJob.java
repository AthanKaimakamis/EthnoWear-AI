package fmi.ethnowear.persistence.jpa.entity.document;

import fmi.ethnowear.domain.model.document.processing.JobStatus;
import fmi.ethnowear.domain.model.document.processing.JobType;
import fmi.ethnowear.persistence.jpa.entity.UpdatableEntity;
import fmi.ethnowear.persistence.jpa.entity.KnowledgeChunk;
import fmi.ethnowear.persistence.jpa.entity.MediaAsset;
import fmi.ethnowear.persistence.jpa.type.SqlServerRowVersionType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Type;
import org.hibernate.generator.EventType;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.regex.Pattern;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "DocumentProcessingJobs", schema = "ethnowear")
public class DocumentProcessingJob extends UpdatableEntity {

    private static final Pattern SHA_256_PATTERN = Pattern.compile("[0-9a-f]{64}");

    @Enumerated(EnumType.STRING)
    @Column(name = "JobType", nullable = false, length = 50)
    private JobType jobType;

    @Enumerated(EnumType.STRING)
    @Column(name = "Status", nullable = false, length = 50)
    private JobStatus status = JobStatus.QUEUED;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "DocumentId")
    private Document document;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "DocumentPageId")
    private DocumentPage documentPage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "InputMediaAssetId")
    private MediaAsset inputMediaAsset;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "KnowledgeChunkId")
    private KnowledgeChunk knowledgeChunk;

    @Setter(AccessLevel.NONE)
    @Column(name = "JobKey", length = 500)
    private String jobKey;

    @Setter(AccessLevel.NONE)
    @Column(name = "ActiveJobKey", length = 500)
    private String activeJobKey;

    @Setter(AccessLevel.NONE)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "PreviousJobId", updatable = false)
    private DocumentProcessingJob previousJob;

    @Column(name = "Priority", nullable = false)
    private Integer priority = 0;

    @Column(name = "AttemptCount", nullable = false)
    private Integer attemptCount = 0;

    @Column(name = "MaxAttempts", nullable = false)
    private Integer maxAttempts = 3;

    @Column(name = "AvailableAt", nullable = false)
    private LocalDateTime availableAt;

    @Column(name = "ClaimedBy", length = 150)
    private String claimedBy;

    @Column(name = "ClaimedAt")
    private LocalDateTime claimedAt;

    @Column(name = "ClaimExpiresAt")
    private LocalDateTime claimExpiresAt;

    @Setter(AccessLevel.NONE)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "ClaimTokenHash", length = 64, columnDefinition = "char(64)")
    private String claimTokenHash;

    @Column(name = "StartedAt")
    private LocalDateTime startedAt;

    @Column(name = "FinishedAt")
    private LocalDateTime finishedAt;

    @Column(name = "TimeoutAt")
    private LocalDateTime timeoutAt;

    @Column(name = "ProcessorName", length = 100)
    private String processorName;

    @Column(name = "ProcessorVersion", length = 100)
    private String processorVersion;

    @Column(name = "ToolName", length = 100)
    private String toolName;

    @Column(name = "ToolVersion", length = 100)
    private String toolVersion;

    @Lob
    @Column(name = "ParametersJson")
    private String parametersJson;

    @Column(name = "ErrorCode", length = 100)
    private String errorCode;

    @Column(name = "SafeErrorMessage", length = 1000)
    private String safeErrorMessage;

    @Lob
    @Column(name = "ErrorDetailsJson")
    private String errorDetailsJson;

    @Column(name = "CancellationReason", length = 500)
    private String cancellationReason;

    @Setter(AccessLevel.NONE)
    @Column(name = "RetiredAt")
    private LocalDateTime retiredAt;

    @Setter(AccessLevel.NONE)
    @Column(name = "RetiredBy", length = 150)
    private String retiredBy;

    @Setter(AccessLevel.NONE)
    @Column(name = "RetirementReason", length = 500)
    private String retirementReason;

    @Column(name = "CorrelationId")
    private UUID correlationId;

    @Version
    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Type(SqlServerRowVersionType.class)
    @Setter(AccessLevel.NONE)
    @Column(
            name = "RowVersion",
            nullable = false,
            insertable = false,
            updatable = false,
            columnDefinition = "binary(8)"
    )
    private byte[] rowVersion;

    public void assignActiveJobKey(String activeJobKey) {
        if (activeJobKey == null || activeJobKey.isBlank())
            throw new IllegalArgumentException("Active job key is required");

        this.activeJobKey = activeJobKey;
    }

    public void assignJobKey(String jobKey) {
        if (jobKey == null || jobKey.isBlank())
            throw new IllegalArgumentException("Job key is required");

        if (this.jobKey != null && !this.jobKey.equals(jobKey))
            throw new IllegalStateException("Job key is immutable");

        this.jobKey = jobKey;
    }

    public void clearActiveJobKey() {
        this.activeJobKey = null;
    }

    public void linkPreviousJob(DocumentProcessingJob previousJob) {
        if (this.previousJob != null && this.previousJob != previousJob)
            throw new IllegalStateException("Previous processing job is immutable");

        this.previousJob = previousJob;
    }

    public void retire(
            LocalDateTime retiredAt,
            String retiredBy,
            String retirementReason
    ) {
        if (this.retiredAt != null)
            return;

        this.retiredAt = java.util.Objects.requireNonNull(
                retiredAt,
                "Retirement time is required"
        );
        this.retiredBy = requireRetirementText(retiredBy, "Retiring user", 150);
        this.retirementReason = requireRetirementText(
                retirementReason,
                "Retirement reason",
                500
        );
    }

    private String requireRetirementText(
            String value,
            String field,
            int maximumLength
    ) {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException(field + " is required");

        String normalized = value.trim();

        if (normalized.length() > maximumLength)
            throw new IllegalArgumentException(
                    field + " cannot exceed " + maximumLength + " characters"
            );

        return normalized;
    }

    public void assignClaimTokenHash(String claimTokenHash) {
        if (claimTokenHash == null || !SHA_256_PATTERN.matcher(claimTokenHash).matches())
            throw new IllegalArgumentException("Claim token hash must be lowercase SHA-256 hexadecimal");

        this.claimTokenHash = claimTokenHash;
    }

    public void clearClaimTokenHash() {
        this.claimTokenHash = null;
    }

    public void clearClaimOwnership() {
        claimedBy = null;
        claimedAt = null;
        claimExpiresAt = null;
        claimTokenHash = null;
        timeoutAt = null;
    }
}
