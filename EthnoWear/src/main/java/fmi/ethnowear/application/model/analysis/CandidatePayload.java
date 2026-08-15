package fmi.ethnowear.application.model.analysis;

import java.util.List;

public record CandidatePayload(
        String id,
        String type,
        int score,
        List<EvidencePayload> evidence
) {
}
