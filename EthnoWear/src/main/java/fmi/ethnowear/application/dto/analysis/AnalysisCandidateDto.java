package fmi.ethnowear.application.dto.analysis;

import java.util.List;

public record AnalysisCandidateDto(
        String id,
        String label,
        String type,
        int score,
        List<AnalysisEvidenceDto> evidence
) {
}
