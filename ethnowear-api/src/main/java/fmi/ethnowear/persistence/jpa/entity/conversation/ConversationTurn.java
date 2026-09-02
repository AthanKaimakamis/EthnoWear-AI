package fmi.ethnowear.persistence.jpa.entity.conversation;

import fmi.ethnowear.domain.model.conversation.ConversationProgressStage;
import fmi.ethnowear.domain.model.conversation.ConversationTurnStatus;
import fmi.ethnowear.persistence.jpa.entity.UpdatableEntity;
import fmi.ethnowear.persistence.jpa.type.SqlServerRowVersionType;
import fmi.ethnowear.util.ContentHashUtils;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.Type;
import org.hibernate.generator.EventType;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "ConversationTurns", schema = "ethnowear")
public class ConversationTurn extends UpdatableEntity {

    @Column(name = "PublicId", nullable = false, updatable = false)
    private UUID publicId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ConversationId", nullable = false, updatable = false)
    private Conversation conversation;

    @Column(name = "ClientRequestId", nullable = false, updatable = false)
    private UUID clientRequestId;

    @Column(name = "TurnSequence", nullable = false, updatable = false)
    private long turnSequence;

    @Column(name = "UserMessage", nullable = false, length = 1000, updatable = false)
    private String userMessage;

    @Column(name = "RequestHash", nullable = false, length = 64, updatable = false)
    private String requestHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "Status", nullable = false, length = 20)
    private ConversationTurnStatus status = ConversationTurnStatus.QUEUED;

    @Enumerated(EnumType.STRING)
    @Column(name = "Stage", length = 50)
    private ConversationProgressStage stage = ConversationProgressStage.RECEIVED;

    @Column(name = "IsActive", nullable = false)
    private boolean active = true;

    @Column(name = "AnswerJson", columnDefinition = "nvarchar(max)")
    private String answerJson;

    @Column(name = "ErrorCode", length = 100)
    private String errorCode;

    @Column(name = "LastEventId", nullable = false)
    private long lastEventId;

    @Column(name = "StartedAt")
    private LocalDateTime startedAt;

    @Column(name = "FinishedAt")
    private LocalDateTime finishedAt;

    @Getter(AccessLevel.NONE)
    @Version
    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Type(SqlServerRowVersionType.class)
    @Column(
            name = "RowVersion",
            nullable = false,
            insertable = false,
            updatable = false,
            columnDefinition = "binary(8)"
    )
    private byte[] rowVersion;

    public ConversationTurn(
            Conversation conversation,
            UUID clientRequestId,
            long turnSequence,
            String userMessage
    ) {
        if (turnSequence < 1)
            throw new IllegalArgumentException("Turn sequence must be positive");

        if (userMessage == null || userMessage.isBlank() || userMessage.length() > 1000)
            throw new IllegalArgumentException("Message must contain 1 to 1000 characters");

        this.publicId = UUID.randomUUID();
        this.conversation = Objects.requireNonNull(conversation);
        this.clientRequestId = Objects.requireNonNull(clientRequestId);
        this.turnSequence = turnSequence;
        this.userMessage = userMessage;
        this.requestHash = ContentHashUtils.sha256(userMessage);
    }

    public void start(LocalDateTime now) {
        requireStatus(ConversationTurnStatus.QUEUED);
        validateTimestamp(now);
        startedAt = now;
        status = ConversationTurnStatus.RUNNING;
    }

    public void advance(ConversationProgressStage stage) {
        requireStatus(ConversationTurnStatus.RUNNING);
        this.stage = Objects.requireNonNull(stage);
    }

    // The service validates and serializes the public answer before calling this.
    public void complete(String validatedAnswerJson, LocalDateTime now) {
        if (validatedAnswerJson == null || validatedAnswerJson.isBlank()
                || validatedAnswerJson.length() > 65536)
            throw new IllegalArgumentException("Validated answer exceeds its allowed bounds");

        if (status == ConversationTurnStatus.COMPLETED) {
            if (!Objects.equals(answerJson, validatedAnswerJson))
                throw new IllegalStateException("Turn already completed with a different answer");
            return;
        }

        requireStatus(ConversationTurnStatus.RUNNING);
        validateTimestamp(now);
        answerJson = validatedAnswerJson;
        finish(ConversationTurnStatus.COMPLETED, now);
    }

    public void fail(String safeErrorCode, LocalDateTime now) {
        if (safeErrorCode == null || !safeErrorCode.matches("[A-Z0-9_]{1,100}"))
            throw new IllegalArgumentException("A safe error code is required");

        if (status == ConversationTurnStatus.FAILED) {
            if (!Objects.equals(errorCode, safeErrorCode))
                throw new IllegalStateException("Turn already failed with a different error code");
            return;
        }

        requireActive();
        validateTimestamp(now);
        errorCode = safeErrorCode;
        finish(ConversationTurnStatus.FAILED, now);
    }

    public void cancel(LocalDateTime now) {
        if (status == ConversationTurnStatus.CANCELLED)
            return;

        requireActive();
        validateTimestamp(now);
        finish(ConversationTurnStatus.CANCELLED, now);
    }

    // Allocate under a turn lock and insert the event in the same transaction.
    public long nextEventId() {
        lastEventId = Math.incrementExact(lastEventId);
        return lastEventId;
    }

    public byte[] getRowVersion() {
        return rowVersion == null ? null : rowVersion.clone();
    }

    private void finish(ConversationTurnStatus terminalStatus, LocalDateTime now) {
        status = terminalStatus;
        active = false;
        stage = null;
        finishedAt = now;
    }

    private void requireActive() {
        if (status != ConversationTurnStatus.QUEUED && status != ConversationTurnStatus.RUNNING)
            throw new IllegalStateException("Turn is already terminal");
    }

    private void requireStatus(ConversationTurnStatus expected) {
        if (status != expected)
            throw new IllegalStateException("Turn is not " + expected);
    }

    private void validateTimestamp(LocalDateTime now) {
        Objects.requireNonNull(now);

        if (getCreatedAt() == null)
            throw new IllegalStateException("Persist and flush the queued turn before execution");

        if (now.isBefore(getCreatedAt()) || (startedAt != null && now.isBefore(startedAt)))
            throw new IllegalArgumentException("Turn timestamp cannot precede creation or execution");
    }
}
