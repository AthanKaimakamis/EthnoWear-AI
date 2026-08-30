package fmi.ethnowear.application.service.analysis;

import fmi.ethnowear.application.dto.analysis.AnalysisCandidateDto;
import fmi.ethnowear.application.dto.analysis.AnalysisEvidenceDto;
import fmi.ethnowear.application.dto.analysis.AnalyzeRequest;
import fmi.ethnowear.application.dto.analysis.AnalyzeResponse;
import fmi.ethnowear.application.model.analysis.*;
import fmi.ethnowear.application.port.analysis.AnalysisGateway;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

import static fmi.ethnowear.util.TextUtils.defaultIfBlank;

@Service
public class InterpretationService {

    private final AnalysisGateway analysisGateway;

    public InterpretationService(AnalysisGateway analysisGateway) {
        this.analysisGateway = analysisGateway;
    }

    public AnalyzeResponse analyze(AnalyzeRequest request){
        String conversationId = UUID.randomUUID().toString();

        AnalyzeFeaturesPayload payload = toAgentPayload(conversationId, request);
        InterpretationResultPayload result = analysisGateway.analyze(payload);

        return toResponse(result);
    }

    @NonNull
    private AnalyzeFeaturesPayload toAgentPayload(String conversationId, @NonNull AnalyzeRequest request){
        return new AnalyzeFeaturesPayload(
                conversationId,
                new SelectedFeaturesPayload(
                        emptyIfNull(request.ornaments()),
                        emptyIfNull(request.colors()),
                        emptyIfNull(request.techniques()),
                        request.motif(),
                        request.region(),
                        request.regionalEmbroidery()
                ),
                normalizeLanguage(request.language())
        );
    }

    @NonNull
    private  AnalyzeResponse toResponse(@NonNull InterpretationResultPayload result){
        List<AnalysisCandidateDto> candidates = result.candidates().stream()
                .map(this::toCandidateDto)
                .toList();
        return new AnalyzeResponse(
                result.conversationId(),
                candidates.isEmpty() ? null : candidates.getFirst(),
                candidates,
                result.explanation(),
                emptyIfNull(result.warnings())
        );
    }

    @NonNull
    private AnalysisCandidateDto toCandidateDto(@NonNull CandidatePayload candidate){
        return new AnalysisCandidateDto(
                candidate.id(),
                null,
                candidate.type(),
                candidate.score(),
                candidate.evidence().stream()
                        .map(this::toEvidenceDto)
                        .toList()
        );
    }

    @NonNull
    private AnalysisEvidenceDto toEvidenceDto(@NonNull EvidencePayload evidence){
        return new AnalysisEvidenceDto(
                evidence.featureType(),
                evidence.selectedFeature(),
                null,
                evidence.matchedProperty(),
                null,
                evidence.weight()
        );
    }

    @NonNull
    private List<String> emptyIfNull(List<String> values) {
        return values == null ? List.of() : values;
    }

    @NonNull
    private String normalizeLanguage(String language) {
        return defaultIfBlank(language, "bg");
    }
}
