package fmi.ethnowear.application.service.conversation;

import fmi.ethnowear.application.dto.catalogue.ConceptEvidenceSummaryDetails;
import fmi.ethnowear.application.dto.catalogue.EntityOntologyDetails;
import fmi.ethnowear.application.service.catalogue.ConceptCardEvidenceService;
import fmi.ethnowear.application.service.catalogue.OntologyEntityDetailReader;
import fmi.ethnowear.application.service.conversation.evidence.ConversationOntologyEvidenceService;
import fmi.ethnowear.domain.model.ontology.FeatureType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConversationOntologyEvidenceServiceTest {

    @Test
    void enrichesMatchedOntologyCardWithRightsClearedRepresentativeMedia() {
        OntologyEntityDetailReader ontologyReader = mock(OntologyEntityDetailReader.class);
        ConceptCardEvidenceService evidenceService = mock(ConceptCardEvidenceService.class);
        EntityOntologyDetails entity = new EntityOntologyDetails(
                FeatureType.TECHNIQUE,
                "urn:technique:chain",
                "ChainTechnique",
                "Синджир бод",
                List.of("синджирен бод"),
                "Описание",
                "bg",
                List.of(),
                Map.of()
        );
        when(ontologyReader.list(FeatureType.TECHNIQUE, "bg"))
                .thenReturn(List.of(entity));
        when(evidenceService.summarize(
                FeatureType.TECHNIQUE,
                List.of("urn:technique:chain")
        )).thenReturn(Map.of(
                "urn:technique:chain",
                new ConceptEvidenceSummaryDetails(1, 51L)
        ));

        var service = new ConversationOntologyEvidenceService(
                ontologyReader,
                evidenceService
        );

        var result = service.resolve("Какво е синджир бод?", "bg");

        assertThat(result.entityCards()).singleElement().satisfies(card ->
                assertThat(card.representativeMediaAssetId()).isEqualTo(51L));
    }
}
