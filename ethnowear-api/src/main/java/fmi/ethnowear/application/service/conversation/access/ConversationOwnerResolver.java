package fmi.ethnowear.application.service.conversation.access;

import fmi.ethnowear.application.model.conversation.ConversationOwner;
import fmi.ethnowear.application.model.publicauth.PublicUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ConversationOwnerResolver {

    private final ConversationGuestSessionService guestSessions;

    public Optional<ConversationOwner> resolve(PublicUserPrincipal principal, String guestToken) {
        if (principal != null)
            return Optional.of(ConversationOwner.forPublicUser(principal.userId()));

        return guestSessions.resolve(guestToken);
    }
}
