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
    void formatsSupportedClaimsWithoutDisplayingUnvalidatedIntroduction() {
        var ontology = new ConversationOntologyEvidence("ontology:TECHNIQUE:ChainTechnique", FeatureType.TECHNIQUE,
                "https://example.org/ChainTechnique", "ChainTechnique", "Синджир бод", "Описание", List.of());
        var evidence = new ConversationEvidenceBundle(List.of(), List.of(ontology), List.of(), List.of(), List.of());
        var claims = List.of(
                new fmi.ethnowear.application.model.conversation.ConversationGeneratedClaim("Първа мисъл.", List.of(ontology.citationId())),
                new fmi.ethnowear.application.model.conversation.ConversationGeneratedClaim("Втора мисъл.", List.of(ontology.citationId())),
                new fmi.ethnowear.application.model.conversation.ConversationGeneratedClaim("Трета мисъл.", List.of(ontology.citationId())));
        var answer = assembler.assemble(context, evidence, new ConversationGenerationResult(
                "Unvalidated introduction", true, claims, List.of(ontology.citationId()), List.of()));
        assertThat(answer.answer()).startsWith("Първа мисъл. Втора мисъл.\n\nТрета мисъл.")
                .contains("не обхваща целия въпрос").doesNotContain("Unvalidated introduction");
    }

    @Test
    void keepsDocumentSourcesCitedButDoesNotRequireCitationForRelatedEntityCard() {
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
                        List.of("chunk:31"),
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
    void retainsRetrievedArchiveCardsAndMediaWithoutRequiringModelCitations() {
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

        assertThat(answer.archiveCards()).extracting("archiveItemId").containsExactly(7L, 8L);
        assertThat(answer.media()).extracting("mediaAssetId").containsExactly(70L, 80L);
        assertThat(answer.sources()).isEmpty();
    }

    @Test
    void listsEveryRegisteredTechniqueWithoutReassigningRegionFactsToStyle() {
        var region = new ConversationOntologyEvidence("ontology:REGION:Example", FeatureType.REGION,
                "urn:example", "Example", "Примерен регион", null,
                java.util.stream.IntStream.rangeClosed(1, 8)
                        .mapToObj(i -> "TECHNIQUE: Бод " + i + " [Stitch" + i + "]").toList());
        var style = new ConversationOntologyEvidence("ontology:REGIONAL_EMBROIDERY:Style", FeatureType.REGIONAL_EMBROIDERY,
                "urn:style", "Style", "Примерна шевица", null, List.of("REGION: Примерен регион [Example]"));
        var evidence = new ConversationEvidenceBundle(List.of(), List.of(region, style), List.of(), List.of(), List.of());
        var listContext = new ConversationTurnExecutionContext(context.conversationId(), context.turnId(), "bg", "кой техники използват там");
        var answer = assembler.assemble(listContext, evidence,
                new ConversationGenerationResult("Кратък отговор.", false, List.of(), List.of(region.citationId()), List.of()));
        assertThat(answer.answer()).contains("Записани връзки за Примерен регион — техники (8)")
                .doesNotContain("Записани връзки за Примерна шевица", "[Stitch");
        for (int i = 1; i <= 8; i++) assertThat(answer.answer()).contains("• Бод " + i);
        String complete = "Примерен регион: " + java.util.stream.IntStream.rangeClosed(1, 8)
                .mapToObj(i -> "Бод " + i).collect(java.util.stream.Collectors.joining(", ")) + ".";
        var completeAnswer = assembler.assemble(listContext, evidence,
                new ConversationGenerationResult(complete, false, List.of(), List.of(region.citationId()), List.of()));
        assertThat(completeAnswer.answer()).isEqualTo(complete);
    }

    @Test
    void relatedCardsRequireRetrievedEvidenceAndMediaRequireAnEligibleCard() {
        var ontology = new ConversationOntologyEvidence("ontology:REGION:R", FeatureType.REGION,
                "urn:r", "R", "Region", null, List.of());
        var evidence = new ConversationEvidenceBundle(List.of(), List.of(ontology), List.of(),
                List.of(new ConversationEntityCardDetails(FeatureType.REGION, "R", "Region", 1L),
                        new ConversationEntityCardDetails(FeatureType.REGION, "Unknown", "Unknown", 2L)),
                List.of(), List.of(new ConversationMediaDetails(1L, MediaType.IMAGE, "Related", "/api/media/1/content", null, FeatureType.REGION, "R"),
                        new ConversationMediaDetails(2L, MediaType.IMAGE, "Unknown", "/api/media/2/content", null, FeatureType.REGION, "Unknown")), List.of());
        var answer = assembler.assemble(context, evidence,
                new ConversationGenerationResult("Partial.", true, List.of(), List.of(), List.of()));
        assertThat(answer.entityCards()).extracting("localName").containsExactly("R");
        assertThat(answer.media()).extracting("mediaAssetId").containsExactly(1L);
        assertThat(answer.sources()).isEmpty();
    }

    @Test
    void englishListIncludesOnlyRequestedRelationshipTypes() {
        var ontology = new ConversationOntologyEvidence("ontology:REGION:R", FeatureType.REGION,
                "urn:r", "R", "Region", null, List.of("COLOR: Red [Red]", "COLOR: Blue [Blue]", "TECHNIQUE: Cross stitch [Cross]"));
        var evidence = new ConversationEvidenceBundle(List.of(), List.of(ontology), List.of(), List.of(), List.of());
        var answer = assembler.assemble(new ConversationTurnExecutionContext(context.conversationId(), context.turnId(), "en", "Which colors are used there?"),
                evidence, new ConversationGenerationResult("Summary.", false, List.of(), List.of(ontology.citationId()), List.of()));
        assertThat(answer.answer()).contains("Registered relationships for Region — colors (2)", "• Red", "• Blue")
                .doesNotContain("Cross stitch");
        var ordinary = assembler.assemble(context, evidence,
                new ConversationGenerationResult("Summary.", false, List.of(), List.of(ontology.citationId()), List.of()));
        assertThat(ordinary.answer()).isEqualTo("Summary.");
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
