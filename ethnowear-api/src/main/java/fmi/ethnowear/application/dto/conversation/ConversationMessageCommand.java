package fmi.ethnowear.application.dto.conversation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record ConversationMessageCommand(
        @NotNull
        UUID clientRequestId,

        @NotBlank
        @Size(max = 1000)
        String text
) {
}
