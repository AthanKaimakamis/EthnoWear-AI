package fmi.ethnowear.application.dto.conversation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import fmi.ethnowear.domain.model.conversation.ConversationProgressStage;
import fmi.ethnowear.domain.model.conversation.ConversationTurnStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConversationReadDetailsTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final Instant createdAt = Instant.parse("2026-08-31T10:00:00Z");

    @Test
    void conversationRoundTripsWithoutOwnershipOrCredentials() throws Exception {
        var details = new ConversationDetails(UUID.randomUUID(), "Embroidery", "bg", createdAt, createdAt);

        assertEquals(details, roundTrip(details, ConversationDetails.class));
        assertEquals(Set.of("conversationId", "title", "language", "createdAt", "updatedAt"), fields(details));
    }

    @Test
    void newConversationCanHaveNoTitle() throws Exception {
        var details = new ConversationDetails(UUID.randomUUID(), null, "en", createdAt, createdAt);

        assertEquals(details, roundTrip(details, ConversationDetails.class));
        assertTrue(objectMapper.valueToTree(details).get("title").isNull());
    }

    @ParameterizedTest
    @EnumSource(ConversationTurnStatus.class)
    void turnRoundTripsItsAuthoritativeLifecycleSnapshot(ConversationTurnStatus status) throws Exception {
        UUID conversationId = UUID.randomUUID();
        UUID turnId = UUID.randomUUID();
        boolean terminal = Set.of(ConversationTurnStatus.COMPLETED, ConversationTurnStatus.FAILED,
                ConversationTurnStatus.CANCELLED).contains(status);
        ConversationAnswerDetails answer = status == ConversationTurnStatus.COMPLETED
                ? new ConversationAnswerDetails(conversationId, turnId, "Limited evidence", true,
                List.of(), List.of(), List.of(), List.of()) : null;
        ConversationProgressStage stage = terminal ? null : status == ConversationTurnStatus.QUEUED
                ? ConversationProgressStage.RECEIVED : ConversationProgressStage.READING_ONTOLOGY;
        var details = new ConversationTurnDetails(conversationId, turnId, 3L, "Question", status,
                stage, answer, status == ConversationTurnStatus.FAILED ? "CONVERSATION_GENERATION_FAILED" : null,
                7L, createdAt, status == ConversationTurnStatus.QUEUED ? null : createdAt.plusSeconds(1),
                terminal ? createdAt.plusSeconds(5) : null);

        assertEquals(details, roundTrip(details, ConversationTurnDetails.class));
        assertEquals(Set.of("conversationId", "turnId", "turnSequence", "userMessage", "status", "stage",
                "answer", "errorCode", "lastEventId", "createdAt", "startedAt", "finishedAt"), fields(details));
        assertEquals(7L, objectMapper.valueToTree(details).get("lastEventId").asLong());
    }

    private Set<String> fields(Object value) {
        Set<String> fields = new HashSet<>();
        objectMapper.valueToTree(value).fieldNames().forEachRemaining(fields::add);
        return fields;
    }

    private <T> T roundTrip(T value, Class<T> type) throws Exception {
        return objectMapper.readValue(objectMapper.writeValueAsString(value), type);
    }
}
