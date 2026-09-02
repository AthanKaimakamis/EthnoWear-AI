package fmi.ethnowear.application.dto.conversation;

import com.fasterxml.jackson.databind.ObjectMapper;
import fmi.ethnowear.domain.model.ontology.FeatureType;
import fmi.ethnowear.domain.model.conversation.ConversationProgressStage;
import fmi.ethnowear.domain.model.conversation.ConversationActionType;
import fmi.ethnowear.domain.model.conversation.ConversationArchiveTarget;
import fmi.ethnowear.domain.model.archive.MediaType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConversationPublicDetailsTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void sourceExposesPublicAttributionWithoutInternalPageOrChunkReferences() throws Exception {
        ConversationSourceDetails source = new ConversationSourceDetails("source-1", 2L, "Book title", "Author");

        assertEquals(Set.of("citationId", "sourceId", "title", "author"), fields(source));
        assertEquals(source, roundTrip(source, ConversationSourceDetails.class));
    }

    @Test
    void sourceAllowsAnUnknownAuthor() throws Exception {
        ConversationSourceDetails source = new ConversationSourceDetails("source-1", 2L, "Book title", null);

        assertEquals(source, roundTrip(source, ConversationSourceDetails.class));
        assertTrue(objectMapper.valueToTree(source).get("author").isNull());
    }

    @Test
    void entityCardExposesOnlyCanonicalNavigationIdentityAndLabel() throws Exception {
        ConversationEntityCardDetails card = new ConversationEntityCardDetails(
                FeatureType.REGION, "ElhovoRegion", "Elhovo", 11L
        );

        assertEquals(Set.of(
                "entityType", "localName", "label", "representativeMediaAssetId"
        ), fields(card));
        assertEquals("REGION", objectMapper.valueToTree(card).get("entityType").asText());
        assertEquals(card, roundTrip(card, ConversationEntityCardDetails.class));
    }

    @Test
    void archiveCardExposesOnlyControlledMediaIdentity() throws Exception {
        ConversationArchiveCardDetails card = new ConversationArchiveCardDetails(
                2L, "Archive title", 12L
        );

        assertEquals(Set.of(
                "archiveItemId", "title", "representativeMediaAssetId"
        ), fields(card));
        assertEquals(card, roundTrip(card, ConversationArchiveCardDetails.class));
    }

    @Test
    void answerCopiesEveryCollectionAndExposesImmutableLists() {
        var source = new ConversationSourceDetails("source-1", 2L, "Book", null);
        var entity = new ConversationEntityCardDetails(FeatureType.REGION, "ElhovoRegion", "Elhovo");
        var archive = new ConversationArchiveCardDetails(2L, "Archive title");
        var media = new ConversationMediaDetails(
                3L, MediaType.IMAGE, "Image", "/api/media/3/content",
                2L, null, null
        );
        var sources = new ArrayList<>(List.of(source));
        var entities = new ArrayList<>(List.of(entity));
        var archives = new ArrayList<>(List.of(archive));
        var mediaItems = new ArrayList<>(List.of(media));
        var warnings = new ArrayList<>(List.of("INSUFFICIENT_EVIDENCE"));
        var action = new ConversationActionDetails(
                ConversationActionType.OPEN_ARCHIVE_FILTER,
                "Show ornaments",
                ConversationArchiveTarget.ORNAMENT,
                new ConversationArchiveFiltersDetails(List.of("AnimalOrnament"), List.of(), List.of())
        );
        var actions = new ArrayList<>(List.of(action));
        var answer = new ConversationAnswerDetails(UUID.randomUUID(), UUID.randomUUID(),
                "Limited evidence", true, sources, entities, archives, mediaItems, actions, warnings);

        sources.clear();
        entities.clear();
        archives.clear();
        mediaItems.clear();
        actions.clear();
        warnings.clear();

        assertEquals(List.of(source), answer.sources());
        assertEquals(List.of(entity), answer.entityCards());
        assertEquals(List.of(archive), answer.archiveCards());
        assertEquals(List.of(media), answer.media());
        assertEquals(List.of(action), answer.actions());
        assertEquals(List.of("INSUFFICIENT_EVIDENCE"), answer.warningCodes());
        assertThrows(UnsupportedOperationException.class, () -> answer.sources().add(source));
        assertThrows(UnsupportedOperationException.class, () -> answer.entityCards().add(entity));
        assertThrows(UnsupportedOperationException.class, () -> answer.archiveCards().add(archive));
        assertThrows(UnsupportedOperationException.class, () -> answer.media().add(media));
        assertThrows(UnsupportedOperationException.class, () -> answer.actions().add(action));
        assertThrows(UnsupportedOperationException.class, () -> answer.warningCodes().add("OTHER"));
    }

    @Test
    void answerRequiresNonNullCollections() {
        UUID conversationId = UUID.randomUUID();
        UUID turnId = UUID.randomUUID();
        assertThrows(NullPointerException.class, () -> new ConversationAnswerDetails(
                conversationId, turnId, "Answer", false,
                null, List.of(), List.of(), List.of(), List.of()));
        assertThrows(NullPointerException.class, () -> new ConversationAnswerDetails(
                conversationId, turnId, "Answer", false,
                List.of(), null, List.of(), List.of(), List.of()));
        assertThrows(NullPointerException.class, () -> new ConversationAnswerDetails(
                conversationId, turnId, "Answer", false,
                List.of(), List.of(), null, List.of(), List.of()));
        assertThrows(NullPointerException.class, () -> new ConversationAnswerDetails(
                conversationId, turnId, "Answer", false,
                List.of(), List.of(), List.of(), null, List.of()));
        assertThrows(NullPointerException.class, () -> new ConversationAnswerDetails(
                conversationId, turnId, "Answer", false,
                List.of(), List.of(), List.of(), List.of(), null));
    }

    @Test
    void answerRoundTripsOnlyThePublicEnvelope() throws Exception {
        var answer = new ConversationAnswerDetails(UUID.randomUUID(), UUID.randomUUID(),
                "No supported answer", true,
                List.of(), List.of(), List.of(), List.of(), List.of());

        assertEquals(answer, roundTrip(answer, ConversationAnswerDetails.class));
        assertEquals(Set.of("conversationId", "turnId", "answer", "insufficientEvidence",
                "sources", "entityCards", "archiveCards", "media", "warningCodes"), fields(answer));
    }

    @Test
    void actionExposesOnlyTheAllowlistedArchiveFilterContract() throws Exception {
        var filters = new ConversationArchiveFiltersDetails(
                List.of("AnimalOrnament"),
                List.of("BirdOrnament"),
                List.of("ElhovoRegion")
        );
        var action = new ConversationActionDetails(
                ConversationActionType.OPEN_ARCHIVE_FILTER,
                "Покажи орнаментите с птици",
                ConversationArchiveTarget.ORNAMENT,
                filters
        );

        assertEquals(Set.of("type", "label", "target", "filters"), fields(action));
        assertEquals(Set.of(
                "categoryLocalNames", "entityLocalNames", "regionLocalNames"
        ), fields(filters));
        assertEquals(action, roundTrip(action, ConversationActionDetails.class));
    }

    @ParameterizedTest
    @EnumSource(ConversationProgressStage.class)
    void progressStagesRoundTripAsStableCodes(ConversationProgressStage stage) throws Exception {
        assertEquals(stage, roundTrip(stage, ConversationProgressStage.class));
        assertEquals(stage.name(), objectMapper.valueToTree(stage).asText());
    }

    private Set<String> fields(Object value) {
        Set<String> names = new HashSet<>();
        objectMapper.valueToTree(value).fieldNames().forEachRemaining(names::add);
        return names;
    }

    private <T> T roundTrip(T value, Class<T> type) throws Exception {
        return objectMapper.readValue(objectMapper.writeValueAsString(value), type);
    }
}
