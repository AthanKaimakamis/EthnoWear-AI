package fmi.ethnowear.persistence.jpa.repository.conversation;

import fmi.ethnowear.persistence.jpa.entity.conversation.ConversationTurnEvidence;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ConversationTurnEvidenceRepository
        extends JpaRepository<ConversationTurnEvidence, Long> {

    List<ConversationTurnEvidence> findByTurn_IdOrderByIdAsc(long turnId, Pageable pageable);

    Optional<ConversationTurnEvidence> findByTurn_IdAndEvidenceKey(long turnId, String evidenceKey);

    List<ConversationTurnEvidence> findByTurn_IdOrderByIdAsc(long turnId);

    @Modifying
    @Query("DELETE FROM ConversationTurnEvidence evidence WHERE evidence.turn.conversation.id = :conversationId")
    int deleteByConversationId(@Param("conversationId") long conversationId);
}
