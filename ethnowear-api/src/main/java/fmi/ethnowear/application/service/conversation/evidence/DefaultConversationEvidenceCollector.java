package fmi.ethnowear.application.service.conversation.evidence;

import fmi.ethnowear.application.dto.retrieval.GroundedRetrievalQuery;
import fmi.ethnowear.application.model.conversation.ConversationEvidenceBundle;
import fmi.ethnowear.application.model.conversation.ConversationTurnExecutionContext;
import fmi.ethnowear.application.port.conversation.ConversationEvidenceCollector;
import fmi.ethnowear.application.service.retrieval.RagRetrievalService;
import fmi.ethnowear.application.service.conversation.turn.ConversationHistoryService;
import fmi.ethnowear.domain.model.conversation.ConversationProgressStage;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
@ConditionalOnBean(RagRetrievalService.class)
public class DefaultConversationEvidenceCollector implements ConversationEvidenceCollector {

    private final ConversationOntologyEvidenceService ontologyEvidenceService;
    private final ConversationArchiveEvidenceService archiveEvidenceService;
    private final ConversationMediaEvidenceService mediaEvidenceService;
    private final RagRetrievalService retrievalService;
    private final ConversationHistoryService historyService;

    @Override
    public ConversationEvidenceBundle collect(
            @NonNull ConversationTurnExecutionContext context,
            @NonNull Consumer<ConversationProgressStage> progress
    ) {
        progress.accept(ConversationProgressStage.RESOLVING_ENTITIES);
        progress.accept(ConversationProgressStage.READING_ONTOLOGY);

        String evidenceQuery = evidenceQuery(context);
        var ontology = ontologyEvidenceService.resolve(evidenceQuery, context.language());
        var archive = archiveEvidenceService.find(ontology.evidence(), context.language());

        progress.accept(ConversationProgressStage.RETRIEVING_SOURCES);

        var retrieval = retrievalService.retrieve(new GroundedRetrievalQuery(evidenceQuery, null));

        var media = mediaEvidenceService.collect(ontology.entityCards(), archive.cards());

        List<String> warnings = new ArrayList<>();

        if (ontology.evidence().isEmpty())
            warnings.add("ONTOLOGY_EVIDENCE_NOT_FOUND");

        if (retrieval.passages().isEmpty())
            warnings.add("DOCUMENT_EVIDENCE_NOT_FOUND");

        if (!ontology.evidence().isEmpty() && archive.evidence().isEmpty())
            warnings.add("ARCHIVE_EVIDENCE_NOT_FOUND");

        return new ConversationEvidenceBundle(
                retrieval.passages(),
                ontology.evidence(),
                archive.evidence(),
                ontology.entityCards(),
                archive.cards(),
                media,
                warnings
        );
    }

    private String evidenceQuery(ConversationTurnExecutionContext context) {
        return fmi.ethnowear.application.service.conversation.orchestration.ConversationPolicyService.evidenceQuery(
                context.userMessage(), historyService.recent(context));
    }
}
