package fmi.ethnowear.persistence.jpa.repository.conversation;

import fmi.ethnowear.persistence.jpa.entity.conversation.ConversationTurn;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConversationTurnRepository extends JpaRepository<ConversationTurn, Long> {

    Page<ConversationTurn> findByConversation_IdOrderByTurnSequenceDesc(long conversationId, Pageable pageable);

    Optional<ConversationTurn> findByConversation_IdAndPublicId(long conversationId, UUID publicId);

    Optional<ConversationTurn> findByConversation_IdAndClientRequestId(long conversationId, UUID clientRequestId);

    Optional<ConversationTurn> findFirstByConversation_IdOrderByTurnSequenceDesc(long conversationId);

    boolean existsByConversation_IdAndActiveTrue(long conversationId);

    long deleteByConversation_Id(long conversationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT turn FROM ConversationTurn turn
            WHERE turn.conversation.id = :conversationId
              AND turn.publicId = :publicId
            """)
    Optional<ConversationTurn> findForUpdate(
            @Param("conversationId") long conversationId,
            @Param("publicId") UUID publicId
    );

    Page<ConversationTurn> findByConversation_IdOrderByTurnSequenceAsc(
            long conversationId,
            Pageable pageable
    );

    @Query("""
        SELECT turn
        FROM ConversationTurn turn
        WHERE turn.conversation.publicId = :conversationId
          AND turn.publicId <> :currentTurnId
          AND turn.status = fmi.ethnowear.domain.model.conversation.ConversationTurnStatus.COMPLETED
        ORDER BY turn.turnSequence DESC
        """)
    List<ConversationTurn> findRecentCompleted(
            @Param("conversationId") UUID conversationId,
            @Param("currentTurnId") UUID currentTurnId,
            Pageable pageable
    );
}
