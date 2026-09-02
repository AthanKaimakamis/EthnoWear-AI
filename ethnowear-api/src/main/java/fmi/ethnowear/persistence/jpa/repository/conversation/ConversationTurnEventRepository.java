package fmi.ethnowear.persistence.jpa.repository.conversation;

import fmi.ethnowear.persistence.jpa.entity.conversation.ConversationTurnEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ConversationTurnEventRepository
        extends JpaRepository<ConversationTurnEvent, Long> {

    List<ConversationTurnEvent> findByTurn_IdAndEventIdGreaterThanOrderByEventIdAsc(
            long turnId,
            long afterEventId,
            Pageable pageable
    );

    @Modifying
    @Query("DELETE FROM ConversationTurnEvent event WHERE event.turn.conversation.id = :conversationId")
    int deleteByConversationId(@Param("conversationId") long conversationId);
}
