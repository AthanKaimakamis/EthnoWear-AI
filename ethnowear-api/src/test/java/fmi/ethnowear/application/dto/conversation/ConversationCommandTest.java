package fmi.ethnowear.application.dto.conversation;

import com.fasterxml.jackson.databind.ObjectMapper;
import fmi.ethnowear.domain.model.conversation.ConversationTurnStatus;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConversationCommandTest {

    private static final UUID REQUEST_ID = UUID.fromString("31cf77c2-e85e-4b7b-b20a-dc4bddb93f42");
    private static ValidatorFactory factory;
    private static Validator validator;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeAll
    static void createValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        factory.close();
    }

    @ParameterizedTest
    @ValueSource(strings = {"bg", "en"})
    void acceptsSupportedLanguages(String language) {
        assertTrue(validator.validate(new ConversationCreateCommand(REQUEST_ID, language)).isEmpty());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "BG", "en-US", "de", " bg"})
    void rejectsMissingOrUnsupportedLanguages(String language) {
        assertEquals(Set.of("language"), invalidFields(new ConversationCreateCommand(REQUEST_ID, language)));
    }

    @Test
    void requiresRequestIdForBothCommands() {
        assertEquals(Set.of("clientRequestId"), invalidFields(new ConversationCreateCommand(null, "bg")));
        assertEquals(Set.of("clientRequestId"), invalidFields(new ConversationMessageCommand(null, "Question")));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void rejectsBlankMessages(String text) {
        assertEquals(Set.of("text"), invalidFields(new ConversationMessageCommand(REQUEST_ID, text)));
    }

    @Test
    void acceptsBulgarianTextWithoutRewritingIt() {
        String text = "\u041a\u0430\u043a\u0432\u043e \u0435 \u0441\u0438\u043d\u0434\u0436\u0438\u0440 \u0431\u043e\u0434?";
        ConversationMessageCommand command = new ConversationMessageCommand(REQUEST_ID, text);

        assertTrue(validator.validate(command).isEmpty());
        assertEquals(text, command.text());
    }

    @Test
    void enforcesMessageLengthBoundary() {
        assertTrue(validator.validate(new ConversationMessageCommand(REQUEST_ID, "a".repeat(1000))).isEmpty());
        assertEquals(Set.of("text"), invalidFields(new ConversationMessageCommand(REQUEST_ID, "a".repeat(1001))));
    }

    @Test
    void trimsAndValidatesConversationTitlesAfterNormalization() {
        ConversationRenameCommand valid = new ConversationRenameCommand("  New title  ");
        assertEquals("New title", valid.title());
        assertTrue(validator.validate(valid).isEmpty());
        assertEquals(Set.of("title"), invalidFields(new ConversationRenameCommand("   ")));
        assertEquals(Set.of("title"), invalidFields(new ConversationRenameCommand("a".repeat(301))));
        assertTrue(validator.validate(new ConversationRenameCommand(" " + "a".repeat(300) + " ")).isEmpty());
    }

    @Test
    void roundTripsOnlyTheDeclaredRequestFields() throws Exception {
        ConversationCreateCommand create = new ConversationCreateCommand(REQUEST_ID, "bg");
        ConversationMessageCommand message = new ConversationMessageCommand(REQUEST_ID, "Question");
        ConversationRenameCommand rename = new ConversationRenameCommand(" New title ");

        assertEquals(create, objectMapper.readValue(objectMapper.writeValueAsString(create), ConversationCreateCommand.class));
        assertEquals(message, objectMapper.readValue(objectMapper.writeValueAsString(message), ConversationMessageCommand.class));
        assertEquals(rename, objectMapper.readValue(objectMapper.writeValueAsString(rename), ConversationRenameCommand.class));
        assertEquals(Set.of("clientRequestId", "language"), jsonFields(create));
        assertEquals(Set.of("clientRequestId", "text"), jsonFields(message));
        assertEquals(Set.of("title"), jsonFields(rename));
    }

    @ParameterizedTest
    @EnumSource(ConversationTurnStatus.class)
    void roundTripsTurnStatus(ConversationTurnStatus status) throws Exception {
        assertEquals(status, objectMapper.readValue(objectMapper.writeValueAsString(status), ConversationTurnStatus.class));
    }

    private Set<String> invalidFields(Object command) {
        return validator.validate(command).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }

    private Set<String> jsonFields(Object value) {
        Set<String> fields = new HashSet<>();
        objectMapper.valueToTree(value).fieldNames().forEachRemaining(fields::add);
        return fields;
    }
}
