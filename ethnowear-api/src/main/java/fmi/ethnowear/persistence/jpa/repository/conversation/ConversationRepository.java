package fmi.ethnowear.persistence.jpa.repository.conversation;

import fmi.ethnowear.persistence.jpa.entity.conversation.Conversation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    Optional<Conversation> findByPublicIdAndPublicUser_Id(UUID publicId, long publicUserId);

    Optional<Conversation> findByPublicIdAndGuestSession_Id(UUID publicId, long guestSessionId);

    Optional<Conversation> findByClientRequestIdAndPublicUser_Id(UUID clientRequestId, long publicUserId);

    Optional<Conversation> findByClientRequestIdAndGuestSession_Id(UUID clientRequestId, long guestSessionId);

    Page<Conversation> findByPublicUser_Id(long publicUserId, Pageable pageable);

    Page<Conversation> findByGuestSession_Id(long guestSessionId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT conversation FROM Conversation conversation WHERE conversation.id = :id")
    Optional<Conversation> findForUpdate(@Param("id") long id);

    Optional<Conversation> findByPublicId(UUID publicId);
}