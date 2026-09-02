package fmi.ethnowear.application.dto.conversation;

public record ConversationArchiveCardDetails(
        Long archiveItemId,
        String title,
        Long representativeMediaAssetId
) {

    public ConversationArchiveCardDetails(Long archiveItemId, String title) {
        this(archiveItemId, title, null);
    }
}
