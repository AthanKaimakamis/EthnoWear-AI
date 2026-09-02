package fmi.ethnowear.application.service.conversation;

import fmi.ethnowear.application.dto.retrieval.GroundedRetrievalDetails;
import fmi.ethnowear.application.dto.retrieval.GroundedRetrievalQuery;
import fmi.ethnowear.application.model.conversation.ConversationTurnExecutionContext;
import fmi.ethnowear.application.model.conversation.ConversationHistoryMessage;
import fmi.ethnowear.application.model.conversation.OntologyConversationEvidenceSelection;
import fmi.ethnowear.application.model.conversation.ConversationArchiveEvidenceSelection;
import fmi.ethnowear.application.service.conversation.evidence.ConversationArchiveEvidenceService;
import fmi.ethnowear.application.service.conversation.evidence.ConversationOntologyEvidenceService;
import fmi.ethnowear.application.service.conversation.evidence.ConversationMediaEvidenceService;
import fmi.ethnowear.application.service.conversation.evidence.DefaultConversationEvidenceCollector;
import fmi.ethnowear.application.service.retrieval.RagRetrievalService;
import fmi.ethnowear.application.service.conversation.turn.ConversationHistoryService;
import fmi.ethnowear.domain.model.conversation.ConversationProgressStage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultConversationEvidenceCollectorTest {

    private final ConversationOntologyEvidenceService ontology =
            mock(ConversationOntologyEvidenceService.class);
    private final ConversationArchiveEvidenceService archive =
            mock(ConversationArchiveEvidenceService.class);
    private final RagRetrievalService retrieval = mock(RagRetrievalService.class);
    private final ConversationMediaEvidenceService media =
            mock(ConversationMediaEvidenceService.class);
    private final ConversationHistoryService history = mock(ConversationHistoryService.class);
    private final DefaultConversationEvidenceCollector collector =
            new DefaultConversationEvidenceCollector(ontology, archive, media, retrieval, history);

    @Test
    void reportsMissingEvidenceAndPublishesStagesInOrder() {
        ConversationTurnExecutionContext context = new ConversationTurnExecutionContext(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "bg",
                "Какво е синджир бод?"
        );
        List<ConversationProgressStage> stages = new ArrayList<>();

        when(ontology.resolve(context.userMessage(), context.language()))
                .thenReturn(new OntologyConversationEvidenceSelection(List.of(), List.of()));
        when(archive.find(List.of(), context.language()))
                .thenReturn(ConversationArchiveEvidenceSelection.empty());
        when(media.collect(List.of(), List.of())).thenReturn(List.of());
        when(retrieval.retrieve(any(GroundedRetrievalQuery.class)))
                .thenReturn(new GroundedRetrievalDetails(context.userMessage(), 0, List.of()));

        var result = collector.collect(context, stages::add);

        assertThat(result.isEmpty()).isTrue();
        assertThat(result.warningCodes()).containsExactly(
                "ONTOLOGY_EVIDENCE_NOT_FOUND",
                "DOCUMENT_EVIDENCE_NOT_FOUND"
        );
        assertThat(stages).containsExactly(
                ConversationProgressStage.RESOLVING_ENTITIES,
                ConversationProgressStage.READING_ONTOLOGY,
                ConversationProgressStage.RETRIEVING_SOURCES
        );
        verify(retrieval).retrieve(new GroundedRetrievalQuery(context.userMessage(), null));
    }

    @Test
    void carriesPreviousQuestionIntoContextualFollowUpEvidenceQuery() {
        ConversationTurnExecutionContext context = new ConversationTurnExecutionContext(
                UUID.randomUUID(), UUID.randomUUID(), "bg", "а кой техники се ползват"
        );
        String expanded = "кои орнаменти се използват в елхово?\nа кой техники се ползват";

        when(history.recent(context)).thenReturn(List.of(new ConversationHistoryMessage(
                "кои орнаменти се използват в елхово?", "Предишен отговор"
        )));
        when(ontology.resolve(expanded, "bg"))
                .thenReturn(new OntologyConversationEvidenceSelection(List.of(), List.of()));
        when(archive.find(List.of(), "bg")).thenReturn(ConversationArchiveEvidenceSelection.empty());
        when(media.collect(List.of(), List.of())).thenReturn(List.of());
        when(retrieval.retrieve(new GroundedRetrievalQuery(expanded, null)))
                .thenReturn(new GroundedRetrievalDetails(expanded, 0, List.of()));

        collector.collect(context, stage -> { });

        verify(ontology).resolve(expanded, "bg");
        verify(retrieval).retrieve(new GroundedRetrievalQuery(expanded, null));
    }
}
