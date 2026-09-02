package fmi.ethnowear.application.model.conversation;

import fmi.ethnowear.domain.model.conversation.ConversationAgentRole;

import java.util.List;
import java.util.Objects;

public record ConversationAgentFinding(
        ConversationAgentRole role,
        String statement,
        List<String> supportingEvidenceIds,
        boolean interpretation
) {

    public ConversationAgentFinding {
        role = Objects.requireNonNull(role);

        if (statement == null || statement.isBlank())
            throw new IllegalArgumentException("Agent finding statement is required");

        statement = statement.trim();
        supportingEvidenceIds = List.copyOf(supportingEvidenceIds);
    }
}