package fmi.ethnowear.application.dto.conversation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

public record ConversationCreateCommand(
        @NotNull
        UUID clientRequestId,

        @NotBlank
        @Pattern(regexp = "bg|en")
        String language
) {
}
