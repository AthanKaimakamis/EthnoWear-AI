package fmi.ethnowear.application.model.conversation;

import java.util.List;

public record ConversationGeneratedClaim(
        String text,
        List<String> evidenceIds
) {

    public ConversationGeneratedClaim {
        evidenceIds = evidenceIds == null
                ? List.of()
                : List.copyOf(evidenceIds);
    }
}
