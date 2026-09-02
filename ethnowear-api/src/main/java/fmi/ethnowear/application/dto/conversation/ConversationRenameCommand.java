package fmi.ethnowear.application.dto.conversation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ConversationRenameCommand(
        @NotBlank
        @Size(max = 300)
        String title
) {
    public ConversationRenameCommand {
        if (title != null)
            title = title.trim();
    }
}
