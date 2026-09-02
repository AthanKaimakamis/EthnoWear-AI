package fmi.ethnowear.application.service.conversation;

import fmi.ethnowear.application.model.conversation.ConversationOwner;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class ConversationOwnerTest {
    @Test
    void ownerIsExplicitlyPublicOrGuestNeverBoth() {
        var publicOwner = ConversationOwner.forPublicUser(1);
        assertThat(publicOwner.isPublicUser()).isTrue();
        assertThat(publicOwner.publicUserId()).isEqualTo(1);
        assertThat(publicOwner.guestSessionId()).isNull();
        assertThat(ConversationOwner.forGuest(1).isPublicUser()).isFalse();
        assertThatThrownBy(() -> new ConversationOwner(1L, 2L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ConversationOwner(null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ConversationOwner.forPublicUser(0)).isInstanceOf(IllegalArgumentException.class);
    }
}
