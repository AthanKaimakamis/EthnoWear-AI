package fmi.ethnowear.application.service.conversation;

import fmi.ethnowear.application.dto.conversation.ConversationArchiveCardDetails;
import fmi.ethnowear.application.dto.conversation.ConversationEntityCardDetails;
import fmi.ethnowear.application.service.conversation.evidence.ConversationMediaEvidenceService;
import fmi.ethnowear.domain.model.ontology.FeatureType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationMediaEvidenceServiceTest {

    private final ConversationMediaEvidenceService service =
            new ConversationMediaEvidenceService();

    @Test
    void returnsControlledDeduplicatedMediaAndPrefersArchiveContext() {
        var archive = new ConversationArchiveCardDetails(7L, "Archive", 51L);
        var entity = new ConversationEntityCardDetails(
                FeatureType.TECHNIQUE,
                "ChainTechnique",
                "Синджир бод",
                51L
        );

        var result = service.collect(List.of(entity), List.of(archive));

        assertThat(result).singleElement().satisfies(media -> {
            assertThat(media.mediaAssetId()).isEqualTo(51L);
            assertThat(media.contentUrl()).isEqualTo("/api/media/51/content");
            assertThat(media.archiveItemId()).isEqualTo(7L);
            assertThat(media.entityType()).isNull();
        });
    }

    @Test
    void ignoresCardsWithoutRepresentativeMedia() {
        var entity = new ConversationEntityCardDetails(
                FeatureType.REGION,
                "ElhovoRegion",
                "Елхово"
        );

        assertThat(service.collect(List.of(entity), List.of())).isEmpty();
    }
}
