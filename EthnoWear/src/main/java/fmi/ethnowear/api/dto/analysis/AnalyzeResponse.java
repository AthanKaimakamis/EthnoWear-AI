package fmi.ethnowear.api.dto.analysis;

import java.util.List;

public record AnalyzeResponse(
        String conversationId,
        AnalysisCandidateDto topCandidate,
        List<AnalysisCandidateDto> candidates,
        String explanation,
        List<String> warnings
) {
}
