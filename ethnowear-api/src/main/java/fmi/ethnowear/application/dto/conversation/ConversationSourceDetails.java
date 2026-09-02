package fmi.ethnowear.application.dto.conversation;

public record ConversationSourceDetails(
        String citationId,
        Long sourceId,
        String title,
        String author
) {
}