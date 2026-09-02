package fmi.ethnowear.application.service.conversation;

import fmi.ethnowear.application.model.conversation.ConversationEvidenceBundle;
import fmi.ethnowear.application.model.conversation.ConversationOntologyEvidence;
import fmi.ethnowear.application.port.ontology.EmbroideryOntologyClient;
import fmi.ethnowear.application.service.conversation.action.ConversationArchiveActionResolver;
import fmi.ethnowear.domain.model.conversation.ConversationActionType;
import fmi.ethnowear.domain.model.conversation.ConversationArchiveTarget;
import fmi.ethnowear.domain.model.ontology.FeatureType;
import fmi.ethnowear.domain.model.ontology.LocalizedOntologyResource;
import fmi.ethnowear.domain.model.ontology.OntologyLanguage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ConversationArchiveActionResolverTest {

    private final EmbroideryOntologyClient ontology = mock(EmbroideryOntologyClient.class);
    private final ConversationArchiveActionResolver resolver = new ConversationArchiveActionResolver(ontology);

    @Test
    void resolvesExactOrnamentAsValidatedEntityFilter() {
        var bird = resource("BirdOrnament", "птичи орнамент", "птица");
        when(ontology.listLocalizedOrnamentTypes(OntologyLanguage.BG)).thenReturn(List.of());
        when(ontology.listLocalizedOrnaments(OntologyLanguage.BG)).thenReturn(List.of(bird));

        var actions = resolver.resolve(
                "Покажи ми орнаменти с птици",
                "bg",
                evidence(FeatureType.ORNAMENT, "BirdOrnament", "Птичи орнамент")
        );

        assertThat(actions).singleElement().satisfies(action -> {
            assertThat(action.type()).isEqualTo(ConversationActionType.OPEN_ARCHIVE_FILTER);
            assertThat(action.target()).isEqualTo(ConversationArchiveTarget.ORNAMENT);
            assertThat(action.label()).isEqualTo("Покажи орнаментите");
            assertThat(action.filters().categoryLocalNames()).isEmpty();
            assertThat(action.filters().entityLocalNames()).containsExactly("BirdOrnament");
            assertThat(action.filters().regionLocalNames()).isEmpty();
        });
    }

    @Test
    void resolvesOntologyCategorySeparatelyFromEntityIdentity() {
        var animal = resource("AnimalOrnament", "animal ornament");
        when(ontology.listLocalizedOrnamentTypes(OntologyLanguage.EN)).thenReturn(List.of(animal));
        when(ontology.listLocalizedOrnaments(OntologyLanguage.EN)).thenReturn(List.of());

        var actions = resolver.resolve(
                "Show me animal ornaments",
                "en",
                emptyEvidence()
        );

        assertThat(actions).singleElement().satisfies(action -> {
            assertThat(action.label()).isEqualTo("Show ornaments");
            assertThat(action.filters().categoryLocalNames()).containsExactly("AnimalOrnament");
            assertThat(action.filters().entityLocalNames()).isEmpty();
        });
    }

    @Test
    void rejectsUnvalidatedEvidenceLocalNames() {
        when(ontology.listLocalizedOrnamentTypes(OntologyLanguage.EN)).thenReturn(List.of());
        when(ontology.listLocalizedOrnaments(OntologyLanguage.EN)).thenReturn(List.of());

        var actions = resolver.resolve(
                "Show ornaments",
                "en",
                evidence(FeatureType.ORNAMENT, "FabricatedOrnament", "Fabricated")
        );

        assertThat(actions).singleElement().satisfies(action ->
                assertThat(action.filters().entityLocalNames()).isEmpty());
    }

    @Test
    void omitsActionsForQuestionsWithoutDiscoveryIntent() {
        assertThat(resolver.resolve(
                "Какво е птичи орнамент?",
                "bg",
                emptyEvidence()
        )).isEmpty();

        verifyNoInteractions(ontology);
    }

    @Test
    void omitsActionsForAmbiguousTargets() {
        assertThat(resolver.resolve(
                "Покажи орнаменти и мотиви",
                "bg",
                emptyEvidence()
        )).isEmpty();

        verifyNoInteractions(ontology);
    }

    private ConversationEvidenceBundle evidence(FeatureType type, String localName, String label) {
        return new ConversationEvidenceBundle(
                List.of(),
                List.of(new ConversationOntologyEvidence(
                        "ontology:" + type + ":" + localName,
                        type,
                        "https://example.org/" + localName,
                        localName,
                        label,
                        null,
                        List.of()
                )),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );
    }

    private ConversationEvidenceBundle emptyEvidence() {
        return new ConversationEvidenceBundle(
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of()
        );
    }

    private LocalizedOntologyResource resource(String localName, String label, String... aliases) {
        return new LocalizedOntologyResource(
                "https://example.org/" + localName,
                localName,
                label,
                List.of(aliases),
                null,
                label.matches(".*[А-Яа-я].*") ? OntologyLanguage.BG : OntologyLanguage.EN
        );
    }
}
