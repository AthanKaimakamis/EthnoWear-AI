package fmi.ethnowear.application.service.analysis;

import fmi.ethnowear.agents.protocol.AnalyzeFeaturesPayload;
import fmi.ethnowear.agents.protocol.CandidatePayload;
import fmi.ethnowear.agents.protocol.EvidencePayload;
import fmi.ethnowear.agents.protocol.ReasoningResultPayload;
import fmi.ethnowear.application.constants.AnalysisCandidateTypes;
import fmi.ethnowear.application.constants.AnalysisFeatureTypes;
import fmi.ethnowear.application.constants.ScoringWeights;
import fmi.ethnowear.ontology.OntologyTerms;
import fmi.ethnowear.ontology.embroidery.EmbroideryOntologyClient;
import fmi.ethnowear.ontology.model.OntologyResource;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class RuleBasedReasoningService {

    private final EmbroideryOntologyClient ontology;

    public RuleBasedReasoningService(EmbroideryOntologyClient ontology) {
        this.ontology = ontology;
    }

    public ReasoningResultPayload reason(@NonNull AnalyzeFeaturesPayload payload) {
        List<CandidatePayload> candidates = ontology.listRegions().stream()
                .map(region -> scoreRegion(region.localName(), payload))
                .filter(candidate -> candidate.score() > 0)
                .sorted((left, right) -> Integer.compare(right.score(), left.score()))
                .toList();

        return new ReasoningResultPayload(payload.conversationId(), candidates);
    }

    @NonNull
    private CandidatePayload scoreRegion(String regionLocalName, @NonNull AnalyzeFeaturesPayload payload) {
        List<EvidencePayload> evidence = new ArrayList<>();

        addMatches(
                evidence,
                AnalysisFeatureTypes.ORNAMENT,
                payload.selectedFeatures().ornaments(),
                ontology.listOrnamentsUsedByRegion(regionLocalName),
                OntologyTerms.ObjectProperties.REGION_USES_ORNAMENT,
                ScoringWeights.ORNAMENT_MATCH
        );

        addMatches(
                evidence,
                AnalysisFeatureTypes.COLOR,
                payload.selectedFeatures().colors(),
                ontology.listColorsUsedByRegion(regionLocalName),
                OntologyTerms.ObjectProperties.REGION_USES_COLOR,
                ScoringWeights.COLOR_MATCH
        );

        addMatches(
                evidence,
                AnalysisFeatureTypes.TECHNIQUE,
                payload.selectedFeatures().techniques(),
                ontology.listTechniquesUsedByRegion(regionLocalName),
                OntologyTerms.ObjectProperties.REGION_USES_TECHNIQUE,
                ScoringWeights.TECHNIQUE_MATCH
        );

        int score = evidence.stream()
                .mapToInt(EvidencePayload::weight)
                .sum();

        return new CandidatePayload(
                regionLocalName,
                AnalysisCandidateTypes.REGION,
                score,
                evidence
        );
    }

    private void addMatches(
            @NonNull List<EvidencePayload> evidence,
            String featureType,
            @NonNull List<String> selectedFeatures,
            @NonNull List<OntologyResource> ontologyFeatures,
            String matchedProperty,
            int weight
    ) {
        List<String> available = ontologyFeatures.stream()
                .map(OntologyResource::localName)
                .toList();

        selectedFeatures.stream()
                .filter(available::contains)
                .map(feature -> new EvidencePayload(featureType, feature, matchedProperty, weight))
                .forEach(evidence::add);
    }
}
