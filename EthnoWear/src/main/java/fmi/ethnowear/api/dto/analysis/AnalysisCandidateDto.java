package fmi.ethnowear.api.dto.analysis;

import java.util.List;

public record AnalysisCandidateDto(
        String id,
        String label,
        String type,
        int score,
        List<AnalysisEvidenceDto> evidence
) {
}
