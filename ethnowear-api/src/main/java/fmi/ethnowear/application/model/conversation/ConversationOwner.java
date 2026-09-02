package fmi.ethnowear.application.model.conversation;

import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;

public record ConversationOwner(
        Long publicUserId,
        Long guestSessionId
) {
    public ConversationOwner {
        if ((publicUserId == null) == (guestSessionId == null))
            throw new IllegalArgumentException("Exactly one conversation owner is required");

        if (publicUserId != null && publicUserId <= 0)
            throw new IllegalArgumentException("Public user id must be positive");

        if (guestSessionId != null && guestSessionId <= 0)
            throw new IllegalArgumentException("Guest session id must be positive");
    }

    @Contract("_ -> new")
    public static @NonNull ConversationOwner forPublicUser(long publicUserId) {
        return new ConversationOwner(publicUserId, null);
    }

    @Contract("_ -> new")
    public static @NonNull ConversationOwner forGuest(long guestSessionId) {
        return new ConversationOwner(null, guestSessionId);
    }

    public boolean isPublicUser() {
        return publicUserId != null;
    }
}
