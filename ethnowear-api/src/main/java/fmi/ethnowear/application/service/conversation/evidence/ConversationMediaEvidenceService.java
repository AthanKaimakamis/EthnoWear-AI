package fmi.ethnowear.application.service.conversation.evidence;

import fmi.ethnowear.application.dto.conversation.ConversationArchiveCardDetails;
import fmi.ethnowear.application.dto.conversation.ConversationEntityCardDetails;
import fmi.ethnowear.application.dto.conversation.ConversationMediaDetails;
import fmi.ethnowear.domain.model.archive.MediaType;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ConversationMediaEvidenceService {

    public List<ConversationMediaDetails> collect(
            @NonNull List<ConversationEntityCardDetails> entityCards,
            @NonNull List<ConversationArchiveCardDetails> archiveCards
    ) {
        Map<Long, ConversationMediaDetails> media = new LinkedHashMap<>();

        archiveCards.forEach(card -> addArchiveMedia(media, card));
        entityCards.forEach(card -> addEntityMedia(media, card));

        return List.copyOf(media.values());
    }

    private void addArchiveMedia(
            Map<Long, ConversationMediaDetails> media,
            @NonNull ConversationArchiveCardDetails card
    ) {
        Long mediaAssetId = card.representativeMediaAssetId();
        if (mediaAssetId == null)
            return;

        media.putIfAbsent(mediaAssetId, new ConversationMediaDetails(
                mediaAssetId,
                MediaType.IMAGE,
                card.title(),
                contentUrl(mediaAssetId),
                card.archiveItemId(),
                null,
                null
        ));
    }

    private void addEntityMedia(
            Map<Long, ConversationMediaDetails> media,
            @NonNull ConversationEntityCardDetails card
    ) {
        Long mediaAssetId = card.representativeMediaAssetId();
        if (mediaAssetId == null)
            return;

        media.putIfAbsent(mediaAssetId, new ConversationMediaDetails(
                mediaAssetId,
                MediaType.IMAGE,
                card.label(),
                contentUrl(mediaAssetId),
                null,
                card.entityType(),
                card.localName()
        ));
    }

    @Contract(pure = true)
    private @NonNull String contentUrl(Long mediaAssetId) {
        return "/api/media/" + mediaAssetId + "/content";
    }
}
