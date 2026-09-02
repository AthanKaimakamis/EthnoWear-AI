package fmi.ethnowear.application.service.conversation;

import fmi.ethnowear.application.dto.conversation.ConversationEntityCardDetails;
import fmi.ethnowear.application.dto.conversation.ConversationMediaDetails;
import fmi.ethnowear.application.dto.retrieval.GroundedPageCitationDetails;
import fmi.ethnowear.application.dto.retrieval.GroundedPassageDetails;
import fmi.ethnowear.application.dto.retrieval.GroundedSourceCitationDetails;
import fmi.ethnowear.application.exception.ConversationGenerationRejectedException;
import fmi.ethnowear.application.model.conversation.ConversationEvidenceBundle;
import fmi.ethnowear.application.model.conversation.ConversationArchiveEvidence;
import fmi.ethnowear.application.model.conversation.ConversationGenerationResult;
import fmi.ethnowear.application.model.conversation.ConversationOntologyEvidence;
import fmi.ethnowear.application.model.conversation.ConversationTurnExecutionContext;
import fmi.ethnowear.application.service.conversation.generation.ConversationAnswerAssembler;
import fmi.ethnowear.application.service.conversation.action.ConversationArchiveActionResolver;
import fmi.ethnowear.domain.model.ontology.FeatureType;
import fmi.ethnowear.domain.model.archive.MediaType;
import fmi.ethnowear.domain.model.archive.ArchiveType;
import fmi.ethnowear.domain.model.archive.TrustedLevel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

class ConversationAnswerAssemblerTest {

    private final ConversationArchiveActionResolver actionResolver = mock(ConversationArchiveActionResolver.class);
    private final ConversationAnswerAssembler assembler = new ConversationAnswerAssembler(actionResolver);
    private final ConversationTurnExecutionContext context = new ConversationTurnExecutionContext(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "bg",
            "Какво е синджир бод?"
    );

    ConversationAnswerAssemblerTest() {
        when(actionResolver.resolve(anyString(), anyString(), any())).thenReturn(List.of());
    }

    @Test
    void exposesOnlyCitedAuthoritativeSourcesAndCards() {
        GroundedSourceCitationDetails source = new GroundedSourceCitationDetails(
                11L, "Българска народна шевица", "Автор", null, null,
                21L, null, null, null, null, null, null
        );
        GroundedPassageDetails passage = new GroundedPassageDetails(
                31L, "Текст", "bg", null, 41L, "Документ",
                List.of(new GroundedPageCitationDetails(51L, 1, 0, "1", null, source)),
                0.9, null, null, null, false
        );
        ConversationOntologyEvidence ontology = new ConversationOntologyEvidence(
                "ontology:TECHNIQUE:ChainTechnique",
                FeatureType.TECHNIQUE,
                "https://example.org/ChainTechnique",
                "ChainTechnique",
                "Синджир бод",
                "Описание",
                List.of()
        );
        ConversationEvidenceBundle evidence = new ConversationEvidenceBundle(
                List.of(passage),
                List.of(ontology),
                List.of(new ConversationEntityCardDetails(
                        FeatureType.TECHNIQUE,
                        "ChainTechnique",
                        "Синджир бод",
                        61L
                )),
                List.of(),
                List.of(new ConversationMediaDetails(
                        61L,
                        MediaType.IMAGE,
                        "Синджир бод",
                        "/api/media/61/content",
                        null,
                        FeatureType.TECHNIQUE,
                        "ChainTechnique"
                )),
                List.of("BASE_WARNING")
        );

        var answer = assembler.assemble(
                context,
                evidence,
                new ConversationGenerationResult(
                        "Отговор",
                        false,
                        List.of(),
                        List.of("chunk:31", "ontology:TECHNIQUE:ChainTechnique"),
                        List.of("MODEL_WARNING")
                )
        );

        assertThat(answer.sources()).singleElement().satisfies(details -> {
            assertThat(details.citationId()).isEqualTo("chunk:31");
            assertThat(details.sourceId()).isEqualTo(11L);
            assertThat(details.title()).isEqualTo("Българска народна шевица");
        });
        assertThat(answer.entityCards()).singleElement().satisfies(details ->
                assertThat(details.localName()).isEqualTo("ChainTechnique"));
        assertThat(answer.media()).singleElement().satisfies(details ->
                assertThat(details.mediaAssetId()).isEqualTo(61L));
        assertThat(answer.warningCodes()).containsExactly("BASE_WARNING", "MODEL_WARNING");
    }

    @Test
    void rejectsUnknownModelCitation() {
        ConversationEvidenceBundle evidence = new ConversationEvidenceBundle(
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of()
        );

        assertThatThrownBy(() -> assembler.assemble(
                context,
                evidence,
                new ConversationGenerationResult(
                        "Отговор",
                        false,
                        List.of(),
                        List.of("chunk:999"),
                        List.of()
                )
        ))
                .isInstanceOf(ConversationGenerationRejectedException.class)
                .hasMessage("Generated answer references unknown evidence");
    }

    @Test
    void exposesOnlyCitedArchiveCardsAndTheirMedia() {
        ConversationArchiveEvidence cited = archiveEvidence(7L, "Цитирана находка");
        ConversationArchiveEvidence unrelated = archiveEvidence(8L, "Несвързана находка");
        ConversationEvidenceBundle evidence = new ConversationEvidenceBundle(
                List.of(),
                List.of(),
                List.of(cited, unrelated),
                List.of(),
                List.of(
                        new fmi.ethnowear.application.dto.conversation.ConversationArchiveCardDetails(7L, "Цитирана находка", 70L),
                        new fmi.ethnowear.application.dto.conversation.ConversationArchiveCardDetails(8L, "Несвързана находка", 80L)
                ),
                List.of(
                        new ConversationMediaDetails(70L, MediaType.IMAGE, "Цитирана", "/api/media/70/content", 7L, null, null),
                        new ConversationMediaDetails(80L, MediaType.IMAGE, "Несвързана", "/api/media/80/content", 8L, null, null)
                ),
                List.of()
        );

        var answer = assembler.assemble(
                context,
                evidence,
                new ConversationGenerationResult(
                        "Отговор",
                        false,
                        List.of(),
                        List.of("archive:7"),
                        List.of()
                )
        );

        assertThat(answer.archiveCards()).extracting("archiveItemId").containsExactly(7L);
        assertThat(answer.media()).extracting("mediaAssetId").containsExactly(70L);
    }

    private ConversationArchiveEvidence archiveEvidence(Long id, String title) {
        return new ConversationArchiveEvidence(
                "archive:" + id,
                id,
                title,
                "Описание на находката",
                ArchiveType.EMBROIDERY_SAMPLE,
                null,
                null,
                null,
                TrustedLevel.VERIFIED,
                11L
        );
    }
}
