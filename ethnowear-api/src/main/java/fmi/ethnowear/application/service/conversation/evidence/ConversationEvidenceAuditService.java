package fmi.ethnowear.application.service.conversation.evidence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fmi.ethnowear.application.model.conversation.ConversationEvidenceBundle;
import fmi.ethnowear.application.model.conversation.ConversationTurnExecutionContext;
import fmi.ethnowear.domain.model.conversation.ConversationEvidenceType;
import fmi.ethnowear.persistence.jpa.entity.conversation.ConversationTurn;
import fmi.ethnowear.persistence.jpa.entity.conversation.ConversationTurnEvidence;
import fmi.ethnowear.persistence.jpa.repository.conversation.ConversationRepository;
import fmi.ethnowear.persistence.jpa.repository.conversation.ConversationTurnEvidenceRepository;
import fmi.ethnowear.persistence.jpa.repository.conversation.ConversationTurnRepository;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ConversationEvidenceAuditService {

    private final ConversationRepository conversations;
    private final ConversationTurnRepository turns;
    private final ConversationTurnEvidenceRepository evidenceRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void record(@NonNull ConversationTurnExecutionContext context, @NonNull ConversationEvidenceBundle bundle) {
        ConversationTurn turn = conversations.findByPublicId(context.conversationId())
                .flatMap(conversation -> turns.findByConversation_IdAndPublicId(
                        conversation.getId(),
                        context.turnId()
                ))
                .orElseThrow(() -> new IllegalStateException("Conversation turn does not exist"));

        Map<String, EvidenceSnapshot> requested = snapshots(bundle);
        Map<String, ConversationTurnEvidence> existing = evidenceRepository
                .findByTurn_IdOrderByIdAsc(turn.getId())
                .stream()
                .collect(
                        LinkedHashMap::new,
                        (values, evidence) -> values.put(evidence.getEvidenceKey(), evidence),
                        LinkedHashMap::putAll
                );

        requested.forEach((key, snapshot) -> {
            ConversationTurnEvidence current = existing.get(key);

            if (current == null) {
                evidenceRepository.save(new ConversationTurnEvidence(
                        turn,
                        key,
                        snapshot.type(),
                        snapshot.json()
                ));
                return;
            }

            if (current.getEvidenceType() != snapshot.type() || !current.getSnapshotJson().equals(snapshot.json()))
                throw new IllegalStateException("Conversation evidence changed for key: " + key);
        });

        evidenceRepository.flush();
    }


    private @NonNull Map<String, EvidenceSnapshot> snapshots(@NonNull ConversationEvidenceBundle bundle) {
        Map<String, EvidenceSnapshot> snapshots = new LinkedHashMap<>();

        bundle.documentPassages().forEach(passage -> add(
                snapshots,
                "chunk:" + passage.chunkId(),
                ConversationEvidenceType.DOCUMENT,
                passage
        ));

        bundle.ontologyEvidence().forEach(evidence -> add(
                snapshots,
                evidence.citationId(),
                ConversationEvidenceType.ONTOLOGY,
                evidence
        ));

        bundle.archiveCards().forEach(card -> add(
                snapshots,
                "archive:" + card.archiveItemId(),
                ConversationEvidenceType.ARCHIVE,
                card
        ));

        return snapshots;
    }

    private void add(
            @NonNull Map<String, EvidenceSnapshot> snapshots,
            String key,
            ConversationEvidenceType type,
            Object value
    ) {
        EvidenceSnapshot snapshot = new EvidenceSnapshot(type, serialize(value));
        EvidenceSnapshot previous = snapshots.putIfAbsent(key, snapshot);

        if (previous != null && !previous.equals(snapshot))
            throw new IllegalStateException("Conflicting conversation evidence key: " + key);
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Conversation evidence could not be serialized", ex);
        }
    }

    private record EvidenceSnapshot(
            ConversationEvidenceType type,
            String json
    ) {
    }
}
