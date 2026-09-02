package fmi.ethnowear.infrastructure.conversation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import fmi.ethnowear.application.exception.ConversationGenerationRejectedException;
import fmi.ethnowear.application.model.conversation.ConversationEvidenceBundle;
import fmi.ethnowear.application.dto.retrieval.GroundedPassageDetails;
import fmi.ethnowear.application.model.conversation.ConversationGenerationRequest;
import fmi.ethnowear.application.model.conversation.ConversationReasoningResult;
import fmi.ethnowear.application.service.conversation.generation.ConversationGeneratedOutputValidator;
import fmi.ethnowear.config.ConversationGenerationProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OllamaConversationGenerationClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null)
            server.stop(0);
    }

    @Test
    void repairsOneInvalidResponse() throws Exception {
        AtomicInteger requests = startServer(
                "not-json",
                generationJson("Поправен отговор")
        );

        var result = client().generate(request());

        assertThat(result.answer()).isEqualTo("Поправен отговор");
        assertThat(requests).hasValue(2);
    }

    @Test
    void stopsAfterOneFailedRepairAttempt() throws Exception {
        AtomicInteger requests = startServer("not-json", "still-not-json");

        assertThatThrownBy(() -> client().generate(request()))
                .isInstanceOf(ConversationGenerationRejectedException.class)
                .hasMessage("Conversation model returned an invalid response after repair");

        assertThat(requests).hasValue(2);
    }

    @Test
    void buildsDisplayedAnswerOnlyFromSupportedClaimSegments() throws Exception {
        String response = objectMapper.writeValueAsString(Map.of(
                "answer", "An extra sentence that is not part of the grounded claims.",
                "insufficientEvidence", false,
                "claims", List.of(Map.of(
                        "text", "Човешките фигури присъстват във везбената орнаментика.",
                        "evidenceIds", List.of("chunk:12")
                )),
                "citedEvidenceIds", List.of("chunk:12"),
                "warningCodes", List.of()
        ));
        AtomicInteger requests = startServer(response);

        var result = client().generate(requestWithEvidence());

        assertThat(result.answer()).isEqualTo("Човешките фигури присъстват във везбената орнаментика.");
        assertThat(requests).hasValue(1);
    }

    private AtomicInteger startServer(String... responses) throws IOException {
        Queue<String> queuedResponses = new ArrayDeque<>(List.of(responses));
        AtomicInteger requests = new AtomicInteger();

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/chat", exchange -> {
            exchange.getRequestBody().readAllBytes();
            requests.incrementAndGet();

            byte[] body = responseEnvelope(queuedResponses.remove())
                    .getBytes(StandardCharsets.UTF_8);

            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        return requests;
    }

    private OllamaConversationGenerationClient client() {
        ConversationGenerationProperties properties = new ConversationGenerationProperties(
                true,
                "http://127.0.0.1:" + server.getAddress().getPort(),
                "qwen3:8b",
                Duration.ofSeconds(2),
                30_000,
                4_000,
                20,
                6,
                12_000
        );

        return new OllamaConversationGenerationClient(
                objectMapper,
                properties,
                new ConversationGeneratedOutputValidator(properties)
        );
    }

    private ConversationGenerationRequest request() {
        return new ConversationGenerationRequest(
                "Какво е шевица?",
                "bg",
                List.of(),
                new ConversationEvidenceBundle(
                        List.of(), List.of(), List.of(), List.of(), List.of()
                ),
                new ConversationReasoningResult(List.of(), List.of())
        );
    }

    private ConversationGenerationRequest requestWithEvidence() {
        var passage = new GroundedPassageDetails(
                12L,
                "Човешките фигури присъстват във везбената орнаментика.",
                "bg", null, 1L, "Българска везбена орнаментика", List.of(),
                0.8, null, null, null, true
        );
        return new ConversationGenerationRequest(
                "Човешки фигури във везбената орнаментика",
                "bg",
                List.of(),
                new ConversationEvidenceBundle(
                        List.of(passage), List.of(), List.of(), List.of(), List.of()
                ),
                new ConversationReasoningResult(List.of(), List.of())
        );
    }

    private String generationJson(String answer) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "answer", answer,
                "insufficientEvidence", true,
                "claims", List.of(),
                "citedEvidenceIds", List.of(),
                "warningCodes", List.of()
        ));
    }

    private String responseEnvelope(String content) throws IOException {
        return objectMapper.writeValueAsString(Map.of(
                "message", Map.of(
                        "role", "assistant",
                        "content", content
                )
        ));
    }
}
