package fmi.ethnowear.application.model.conversation;

import java.util.List;

public record ConversationGenerationResult(
        String answer,
        boolean insufficientEvidence,
        List<ConversationGeneratedClaim> claims,
        List<String> citedEvidenceIds,
        List<String> warningCodes
) {

    public ConversationGenerationResult {
        if (answer == null || answer.isBlank())
            throw new IllegalArgumentException("Generated answer is required");

        answer = answer.trim();
        claims = claims == null ? List.of() : List.copyOf(claims);
        citedEvidenceIds = List.copyOf(citedEvidenceIds);
        warningCodes = List.copyOf(warningCodes);
    }
}
