package fmi.ethnowear.persistence.jpa.entity.conversation;

import fmi.ethnowear.domain.model.conversation.ConversationProgressStage;
import fmi.ethnowear.domain.model.conversation.ConversationTurnStatus;
import fmi.ethnowear.persistence.jpa.entity.AppendOnlyEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.util.Objects;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Immutable
@Entity
@Table(name = "ConversationTurnEvents", schema = "ethnowear")
public class ConversationTurnEvent extends AppendOnlyEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ConversationTurnId", nullable = false, updatable = false)
    private ConversationTurn turn;

    @Column(name = "EventId", nullable = false, updatable = false)
    private long eventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "Status", nullable = false, length = 20, updatable = false)
    private ConversationTurnStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "Stage", length = 50, updatable = false)
    private ConversationProgressStage stage;

    @Column(name = "ErrorCode", length = 100, updatable = false)
    private String errorCode;

    public ConversationTurnEvent(ConversationTurn turn) {
        this.turn = Objects.requireNonNull(turn);
        this.eventId = turn.nextEventId();
        this.status = turn.getStatus();
        this.stage = turn.getStage();
        this.errorCode = turn.getErrorCode();
    }
}