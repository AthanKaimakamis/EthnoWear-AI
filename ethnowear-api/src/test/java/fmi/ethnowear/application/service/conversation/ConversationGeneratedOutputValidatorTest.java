package fmi.ethnowear.application.service.conversation;

import fmi.ethnowear.application.dto.retrieval.GroundedPassageDetails;
import fmi.ethnowear.application.exception.ConversationGenerationRejectedException;
import fmi.ethnowear.application.model.conversation.ConversationEvidenceBundle;
import fmi.ethnowear.application.model.conversation.ConversationGenerationRequest;
import fmi.ethnowear.application.model.conversation.ConversationGenerationResult;
import fmi.ethnowear.application.model.conversation.ConversationGeneratedClaim;
import fmi.ethnowear.application.model.conversation.ConversationReasoningResult;
import fmi.ethnowear.application.service.conversation.generation.ConversationGeneratedOutputValidator;
import fmi.ethnowear.config.ConversationGenerationProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConversationGeneratedOutputValidatorTest {

    private static final String SOURCE = "едно две три четири пет шест седем осем девет десет "
            + "единадесет дванадесет тринадесет четиринадесет петнадесет шестнадесет "
            + "седемнадесет осемнадесет деветнадесет двадесет двадесет и едно";

    private final ConversationGeneratedOutputValidator validator =
            new ConversationGeneratedOutputValidator(new ConversationGenerationProperties(
                    true,
                    "http://localhost:11434",
                    "qwen3:8b",
                    Duration.ofSeconds(60),
                    30_000,
                    4_000,
                    20,
                    6,
                    12_000
            ));

    @Test
    void acceptsParaphrasedGroundedAnswer() {
        assertThatCode(() -> validator.validate(
                request(SOURCE),
                result("Източникът съдържа едно и четири.")
        )).doesNotThrowAnyException();
    }

    @Test
    void rejectsUnsupportedClaimEvenWhenCitationIdExists() {
        assertThatThrownBy(() -> validator.validate(
                request("Синджир бод се изпълнява с последователни бримки."),
                result("Този бод произхожда от Япония и се използва за метални изделия.")
        ))
                .isInstanceOf(ConversationGenerationRejectedException.class)
                .hasMessage("Generated factual claim is not supported by its cited evidence");
    }

    @Test
    void rejectsTwentyWordVerbatimSequence() {
        assertThatThrownBy(() -> validator.validate(request(SOURCE), result(SOURCE)))
                .isInstanceOf(ConversationGenerationRejectedException.class)
                .hasMessage("Generated answer reproduces excessive source text");
    }

    @Test
    void rejectsInternalMetadataTerms() {
        assertThatThrownBy(() -> validator.validate(
                request(SOURCE),
                result("Вътрешният ContentHash не трябва да се показва.")
        ))
                .isInstanceOf(ConversationGenerationRejectedException.class)
                .hasMessage("Generated answer contains internal information");
    }

    @Test
    void rejectsGeneratedUrl() {
        assertThatThrownBy(() -> validator.validate(
                request(SOURCE),
                result("Повече информация има на https://example.com.")
        ))
                .isInstanceOf(ConversationGenerationRejectedException.class)
                .hasMessage("Generated answer contains an unsupported URL");
    }

    @Test
    void rejectsGeneratedHtml() {
        assertThatThrownBy(() -> validator.validate(
                request(SOURCE),
                result("<strong>Отговор</strong>")
        ))
                .isInstanceOf(ConversationGenerationRejectedException.class)
                .hasMessage("Generated answer contains HTML");
    }

    @Test
    void rejectsAnswerInWrongLanguage() {
        assertThatThrownBy(() -> validator.validate(
                request(SOURCE),
                result("This answer is written only in English.")
        ))
                .isInstanceOf(ConversationGenerationRejectedException.class)
                .hasMessage("Generated answer does not use the requested language");
    }

    private ConversationGenerationRequest request(String excerpt) {
        return new ConversationGenerationRequest(
                "Какво описва източникът?",
                "bg",
                List.of(),
                new ConversationEvidenceBundle(
                        List.of(new GroundedPassageDetails(
                                1L,
                                excerpt,
                                "bg",
                                null,
                                2L,
                                "Документ",
                                List.of(),
                                0.9,
                                null,
                                null,
                                null,
                                false
                        )),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of()
                ),
                new ConversationReasoningResult(List.of(), List.of())
        );
    }

    private ConversationGenerationResult result(String answer) {
        return new ConversationGenerationResult(
                answer,
                false,
                List.of(new ConversationGeneratedClaim(answer, List.of("chunk:1"))),
                List.of("chunk:1"),
                List.of()
        );
    }
}
