package fmi.ethnowear.persistence.jpa.entity.document;

import fmi.ethnowear.domain.model.document.processing.JobStatus;
import fmi.ethnowear.persistence.jpa.entity.UpdatableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "DocumentProcessingJobAttempts",
        schema = "ethnowear",
        uniqueConstraints = @UniqueConstraint(
                name = "UQ_DocumentProcessingJobAttempts_Job_Execution",
                columnNames = {"ProcessingJobId", "ExecutionNumber"}
        )
)
public class DocumentProcessingJobAttempt extends UpdatableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ProcessingJobId", nullable = false, updatable = false)
    private DocumentProcessingJob processingJob;

    @Column(name = "ExecutionNumber", nullable = false, updatable = false)
    private Integer executionNumber;

    @Column(name = "AttemptNumber", nullable = false, updatable = false)
    private Integer attemptNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "Status", nullable = false, length = 50)
    private JobStatus status;

    @Column(name = "ClaimedBy", length = 150, updatable = false)
    private String claimedBy;

    @Column(name = "ClaimedAt", nullable = false, updatable = false)
    private LocalDateTime claimedAt;

    @Column(name = "StartedAt", updatable = false)
    private LocalDateTime startedAt;

    @Column(name = "FinishedAt")
    private LocalDateTime finishedAt;

    @Column(name = "ProcessorName", length = 100)
    private String processorName;

    @Column(name = "ProcessorVersion", length = 100)
    private String processorVersion;

    @Column(name = "ToolName", length = 100)
    private String toolName;

    @Column(name = "ToolVersion", length = 100)
    private String toolVersion;

    @Column(name = "ErrorCode", length = 100)
    private String errorCode;

    @Column(name = "SafeErrorMessage", length = 1000)
    private String safeErrorMessage;

    @Column(name = "CancellationReason", length = 500)
    private String cancellationReason;
}
