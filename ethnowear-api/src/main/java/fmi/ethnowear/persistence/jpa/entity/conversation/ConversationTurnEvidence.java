package fmi.ethnowear.persistence.jpa.entity.conversation;

import fmi.ethnowear.domain.model.conversation.ConversationEvidenceType;
import fmi.ethnowear.persistence.jpa.entity.AppendOnlyEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Immutable;
import java.util.Objects;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Immutable
@Entity
@Table(name = "ConversationTurnEvidence", schema = "ethnowear")
public class ConversationTurnEvidence extends AppendOnlyEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ConversationTurnId", nullable = false, updatable = false)
    private ConversationTurn turn;

    @Column(name = "EvidenceKey", nullable = false, length = 100, updatable = false)
    private String evidenceKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "EvidenceType", nullable = false, length = 20, updatable = false)
    private ConversationEvidenceType evidenceType;

    @Column(name = "SnapshotJson", nullable = false,
            updatable = false, columnDefinition = "nvarchar(max)")
    private String snapshotJson;

    public ConversationTurnEvidence(
            ConversationTurn turn,
            String evidenceKey,
            ConversationEvidenceType evidenceType,
            String snapshotJson
    ) {
        if (evidenceKey == null || evidenceKey.isBlank() || evidenceKey.length() > 100)
            throw new IllegalArgumentException("Evidence key must contain 1 to 100 characters");

        if (snapshotJson == null || snapshotJson.isBlank() || snapshotJson.length() > 16384)
            throw new IllegalArgumentException("Evidence snapshot exceeds its allowed bounds");

        this.turn = Objects.requireNonNull(turn);
        this.evidenceKey = evidenceKey;
        this.evidenceType = Objects.requireNonNull(evidenceType);
        this.snapshotJson = snapshotJson;
    }
}