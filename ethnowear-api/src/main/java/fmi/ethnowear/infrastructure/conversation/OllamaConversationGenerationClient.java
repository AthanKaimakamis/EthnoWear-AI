package fmi.ethnowear.infrastructure.conversation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fmi.ethnowear.application.exception.ConversationGenerationUnavailableException;
import fmi.ethnowear.application.exception.ConversationGenerationRejectedException;
import fmi.ethnowear.application.model.conversation.ConversationGenerationRequest;
import fmi.ethnowear.application.model.conversation.ConversationGenerationResult;
import fmi.ethnowear.application.model.conversation.ConversationGeneratedClaim;
import fmi.ethnowear.application.port.conversation.ConversationGenerationGateway;
import fmi.ethnowear.application.conversation.prompt.ConversationPromptContext;
import fmi.ethnowear.application.service.conversation.generation.ConversationGeneratedOutputValidator;
import fmi.ethnowear.config.ConversationGenerationProperties;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

import static fmi.ethnowear.application.conversation.prompt.ConversationPrompts.system;
import static fmi.ethnowear.application.conversation.prompt.ConversationPrompts.user;
import static fmi.ethnowear.application.conversation.prompt.ConversationPrompts.repair;

@Component
@Slf4j
@ConditionalOnProperty(
        prefix = "ethnowear.conversation.generation",
        name = "enabled",
        havingValue = "true"
)
public class OllamaConversationGenerationClient implements ConversationGenerationGateway {

    private final RestClient client;
    private final ObjectMapper objectMapper;
    private final ConversationGenerationProperties properties;
    private final ConversationGeneratedOutputValidator outputValidator;

    public OllamaConversationGenerationClient(
            ObjectMapper objectMapper,
            ConversationGenerationProperties properties,
            ConversationGeneratedOutputValidator outputValidator
    ) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.outputValidator = outputValidator;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();

        requestFactory.setConnectTimeout(properties.timeout());
        requestFactory.setReadTimeout(properties.timeout());

        this.client = RestClient.builder()
                .baseUrl(properties.ollamaBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public ConversationGenerationResult generate(@NonNull ConversationGenerationRequest request) {
        try {
            String promptContext = objectMapper.writeValueAsString(ConversationPromptContext.from(request));

            if (promptContext.length() > properties.maximumEvidenceCharacters())
                throw unavailable("Conversation evidence exceeds the configured limit");

            List<OllamaMessage> initialMessages = List.of(
                    new OllamaMessage("system", system()),
                    new OllamaMessage(
                            "user",
                            user(
                                    request.language(),
                                    request.question(),
                                    promptContext
                            )
                    )
            );

            String initialResponse = requestModel(initialMessages);

            try {
                return parseAndValidate(request, initialResponse);
            } catch (
                    JsonProcessingException
                    | ConversationGenerationRejectedException exception
            ) {
                log.warn("Conversation model response required repair: {}", exception.getMessage());
                String repairedResponse = requestModel(List.of(
                        initialMessages.get(0),
                        initialMessages.get(1),
                        new OllamaMessage("assistant", initialResponse),
                        new OllamaMessage("user", repair())
                ));

                try {
                    return parseAndValidate(request, repairedResponse);
                } catch (JsonProcessingException | ConversationGenerationRejectedException ex) {
                    log.warn("Conversation model repair response was rejected: {}", ex.getMessage());
                    throw rejected("Conversation model returned an invalid response after repair", ex);
                }
            }
        } catch (RestClientException | JsonProcessingException ex) {
            throw unavailable("Conversation generation service is unavailable", ex);
        }
    }

    private String requestModel(@NonNull List<OllamaMessage> messages) {
        OllamaChatResponse response = client.post()
                .uri("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new OllamaChatRequest(
                        properties.model(),
                        messages,
                        false,
                        false,
                        "json",
                        Map.of(
                                "temperature", 0.2,
                                "num_predict", 1200
                        )
                ))
                .retrieve()
                .body(OllamaChatResponse.class);

        if (response == null
                || response.message() == null
                || response.message().content() == null
                || response.message().content().isBlank())
            throw unavailable("Conversation model returned an empty response");

        return response.message().content();
    }

    private ConversationGenerationResult parseAndValidate(
            @NonNull ConversationGenerationRequest request,
            @NonNull String content
    ) throws JsonProcessingException {
        RawGenerationResult raw = objectMapper.readValue(
                content,
                RawGenerationResult.class
        );

        List<ConversationGeneratedClaim> claims = raw.claims() == null ? List.of() : raw.claims();
        String answer = claims.isEmpty()
                ? raw.answer()
                : claims.stream()
                        .map(ConversationGeneratedClaim::text)
                        .map(value -> value == null ? "" : value.trim())
                        .collect(java.util.stream.Collectors.joining(" "));

        ConversationGenerationResult result = new ConversationGenerationResult(
                answer,
                raw.insufficientEvidence(),
                claims,
                raw.citedEvidenceIds() == null ? List.of() : raw.citedEvidenceIds(),
                raw.warningCodes() == null ? List.of() : raw.warningCodes()
        );

        outputValidator.validate(request, result);

        return result;
    }


    @Contract("_ -> new")
    private @NonNull ConversationGenerationUnavailableException unavailable(String message) {
        return new ConversationGenerationUnavailableException(message);
    }

    @Contract("_, _ -> new")
    private @NonNull ConversationGenerationUnavailableException unavailable(String message, Throwable cause) {
        return new ConversationGenerationUnavailableException(message, cause);
    }

    @Contract("_, _ -> new")
    private @NonNull ConversationGenerationRejectedException rejected(String message, Throwable cause) {
        return new ConversationGenerationRejectedException(message, cause);
    }

    private record OllamaChatRequest(
            String model,
            List<OllamaMessage> messages,
            boolean stream,
            boolean think,
            String format,
            Map<String, Object> options
    ) {
    }

    private record OllamaMessage(
            String role,
            String content
    ) {
    }

    private record OllamaChatResponse(
            OllamaMessage message
    ) {
    }

    private record RawGenerationResult(
            String answer,
            boolean insufficientEvidence,
            List<ConversationGeneratedClaim> claims,
            List<String> citedEvidenceIds,
            List<String> warningCodes
    ) {
    }
}
