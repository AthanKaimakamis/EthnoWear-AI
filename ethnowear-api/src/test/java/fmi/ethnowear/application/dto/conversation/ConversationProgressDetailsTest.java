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
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConversationProgressDetailsTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @ParameterizedTest
    @EnumSource(ConversationTurnStatus.class)
    void acceptanceCanRepresentTheExistingTurnStatus(ConversationTurnStatus status) throws Exception {
        var details = new ConversationTurnAcceptedDetails(UUID.randomUUID(), UUID.randomUUID(), status);

        assertEquals(details, objectMapper.readValue(objectMapper.writeValueAsString(details),
                ConversationTurnAcceptedDetails.class));
        Set<String> fields = new HashSet<>();
        objectMapper.valueToTree(details).fieldNames().forEachRemaining(fields::add);
        assertEquals(Set.of("conversationId", "turnId", "status"), fields);
    }

    @Test
    void progressPreservesIdentifiersSequenceAndUtcTimestamp() throws Exception {
        var details = new ConversationProgressDetails(42L, UUID.randomUUID(), UUID.randomUUID(),
                ConversationTurnStatus.RUNNING, ConversationProgressStage.READING_ONTOLOGY,
                null, Instant.parse("2026-08-31T10:00:00Z"));
        var json = objectMapper.valueToTree(details);

        assertEquals(details, objectMapper.treeToValue(json, ConversationProgressDetails.class));
        assertEquals(42L, json.get("eventId").asLong());
        assertEquals("2026-08-31T10:00:00Z", json.get("occurredAt").asText());
        assertEquals("READING_ONTOLOGY", json.get("stage").asText());
        assertTrue(json.get("errorCode").isNull());
        Set<String> fields = new HashSet<>();
        json.fieldNames().forEachRemaining(fields::add);
        assertEquals(Set.of("eventId", "conversationId", "turnId", "status", "stage",
                "errorCode", "occurredAt"), fields);
    }

    @ParameterizedTest
    @EnumSource(value = ConversationTurnStatus.class, names = {"COMPLETED", "FAILED", "CANCELLED"})
    void terminalEventRoundTripsWithoutAProcessingStage(ConversationTurnStatus status) throws Exception {
        String errorCode = status == ConversationTurnStatus.FAILED ? "CONVERSATION_GENERATION_FAILED" : null;
        var details = new ConversationProgressDetails(43L, UUID.randomUUID(), UUID.randomUUID(),
                status, null, errorCode, Instant.parse("2026-08-31T10:01:00Z"));
        var json = objectMapper.valueToTree(details);

        assertEquals(details, objectMapper.treeToValue(json, ConversationProgressDetails.class));
        assertTrue(json.get("stage").isNull());
        assertEquals(errorCode, json.get("errorCode").isNull() ? null : json.get("errorCode").asText());
    }
}
