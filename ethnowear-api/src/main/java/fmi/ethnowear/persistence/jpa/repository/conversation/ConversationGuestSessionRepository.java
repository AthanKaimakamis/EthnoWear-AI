package fmi.ethnowear.persistence.jpa.repository.conversation;

import fmi.ethnowear.persistence.jpa.entity.conversation.ConversationGuestSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface ConversationGuestSessionRepository
        extends JpaRepository<ConversationGuestSession, Long> {

    Optional<ConversationGuestSession> findByTokenHashAndRevokedAtIsNullAndExpiresAtAfter(
            String tokenHash,
            LocalDateTime now
    );
}