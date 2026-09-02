package fmi.ethnowear.application.service.conversation;

import fmi.ethnowear.application.model.conversation.ConversationOntologyEvidence;
import fmi.ethnowear.application.service.conversation.evidence.ConversationArchiveEvidenceService;
import fmi.ethnowear.application.service.archive.media.asset.PublicRepresentativeMediaService;
import fmi.ethnowear.domain.model.ontology.FeatureType;
import fmi.ethnowear.persistence.jpa.projection.ConversationArchiveCardProjection;
import fmi.ethnowear.persistence.jpa.repository.ArchiveItemRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConversationArchiveEvidenceServiceTest {

    private final ArchiveItemRepository repository = mock(ArchiveItemRepository.class);
    private final PublicRepresentativeMediaService representativeMediaService =
            mock(PublicRepresentativeMediaService.class);
    private final ConversationArchiveEvidenceService service =
            new ConversationArchiveEvidenceService(repository, representativeMediaService);

    @Test
    void returnsBoundedLocalizedPublishedCards() {
        ConversationArchiveCardProjection projection = mock(ConversationArchiveCardProjection.class);
        when(projection.getArchiveItemId()).thenReturn(7L);
        when(projection.getTitleBg()).thenReturn("Архивен пример");
        when(projection.getTitleEn()).thenReturn(null);
        when(repository.findPublishedConversationCards(eq(List.of("urn:technique:chain")), any()))
                .thenReturn(List.of(projection));
        when(representativeMediaService.findByArchiveItemIds(List.of(7L)))
                .thenReturn(Map.of(7L, 91L));

        var result = service.find(
                List.of(new ConversationOntologyEvidence(
                        "ontology:TECHNIQUE:ChainTechnique",
                        FeatureType.TECHNIQUE,
                        "urn:technique:chain",
                        "ChainTechnique",
                        "Синджир бод",
                        null,
                        List.of()
                )),
                "en"
        );

        assertThat(result.cards()).singleElement().satisfies(card -> {
            assertThat(card.archiveItemId()).isEqualTo(7L);
            assertThat(card.title()).isEqualTo("Архивен пример");
            assertThat(card.representativeMediaAssetId()).isEqualTo(91L);
        });
        assertThat(result.evidence()).singleElement().satisfies(item -> {
            assertThat(item.citationId()).isEqualTo("archive:7");
            assertThat(item.archiveItemId()).isEqualTo(7L);
        });

        verify(repository).findPublishedConversationCards(
                eq(List.of("urn:technique:chain")),
                any(Pageable.class)
        );
    }
}
