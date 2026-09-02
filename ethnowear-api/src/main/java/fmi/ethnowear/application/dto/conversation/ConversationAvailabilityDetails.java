package fmi.ethnowear.application.dto.conversation;

import java.util.List;

public record ConversationAvailabilityDetails(boolean available, boolean ollamaAvailable, boolean ragAvailable, List<String> unavailableCodes) {
    public ConversationAvailabilityDetails {
        unavailableCodes = unavailableCodes == null ? List.of() : List.copyOf(unavailableCodes);
    }
}
