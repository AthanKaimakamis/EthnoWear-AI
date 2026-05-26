package fmi.ethnowear.application.service.analysis;

import fmi.ethnowear.agents.protocol.*;
import fmi.ethnowear.api.dto.analysis.AnalysisCandidateDto;
import fmi.ethnowear.api.dto.analysis.AnalysisEvidenceDto;
import fmi.ethnowear.api.dto.analysis.AnalyzeRequest;
import fmi.ethnowear.api.dto.analysis.AnalyzeResponse;
import fmi.ethnowear.application.service.agent.AgentAnalysisGateway;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class InterpretationService {

    private final AgentAnalysisGateway agentAnalysisGateway;

    public InterpretationService(AgentAnalysisGateway agentAnalysisGateway) {
        this.agentAnalysisGateway = agentAnalysisGateway;
    }

    public AnalyzeResponse analyze(AnalyzeRequest request){
        String conversationId = UUID.randomUUID().toString();

        AnalyzeFeaturesPayload payload = toAgentPayload(conversationId, request);
        InterpretationResultPayload result = agentAnalysisGateway.analyze(payload);

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
        return language == null || language.isBlank() ? "bg" : language;
    }
}
