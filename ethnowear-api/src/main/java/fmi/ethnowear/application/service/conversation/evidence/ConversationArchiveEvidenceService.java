package fmi.ethnowear.application.service.conversation.evidence;

import fmi.ethnowear.application.dto.conversation.ConversationArchiveCardDetails;
import fmi.ethnowear.application.model.conversation.ConversationArchiveEvidence;
import fmi.ethnowear.application.model.conversation.ConversationArchiveEvidenceSelection;
import fmi.ethnowear.application.model.conversation.ConversationOntologyEvidence;
import fmi.ethnowear.application.service.archive.media.asset.PublicRepresentativeMediaService;
import fmi.ethnowear.persistence.jpa.projection.ConversationArchiveCardProjection;
import fmi.ethnowear.persistence.jpa.repository.ArchiveItemRepository;
import fmi.ethnowear.util.TextUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConversationArchiveEvidenceService {

    private static final int MAXIMUM_ARCHIVE_CARDS = 12;
    private static final int MAXIMUM_ARCHIVE_DESCRIPTION_CHARACTERS = 2_000;
    private static final int MAXIMUM_ARCHIVE_FACT_CHARACTERS = 500;

    private final ArchiveItemRepository repository;
    private final PublicRepresentativeMediaService representativeMediaService;

    public ConversationArchiveEvidenceSelection find(
            List<ConversationOntologyEvidence> ontologyEvidence,
            String language
    ) {
        List<String> ontologyIris = ontologyEvidence.stream()
                .map(ConversationOntologyEvidence::iri)
                .filter(TextUtils::isNotBlank)
                .distinct()
                .toList();

        if (ontologyIris.isEmpty())
            return ConversationArchiveEvidenceSelection.empty();

        var pageable = PageRequest.of(
                0,
                MAXIMUM_ARCHIVE_CARDS,
                Sort.by(
                        Sort.Order.desc("publishedAt"),
                        Sort.Order.desc("id")
                )
        );

        List<ConversationArchiveCardProjection> items =
                repository.findPublishedConversationCards(ontologyIris, pageable);
        Map<Long, Long> representativeMedia = representativeMediaService
                .findByArchiveItemIds(items.stream()
                        .map(ConversationArchiveCardProjection::getArchiveItemId)
                        .toList());

        List<ConversationArchiveCardDetails> cards = items.stream()
                .map(item -> new ConversationArchiveCardDetails(
                        item.getArchiveItemId(),
                        localizedTitle(item.getTitleBg(), item.getTitleEn(), language),
                        representativeMedia.get(item.getArchiveItemId())
                ))
                .toList();

        List<ConversationArchiveEvidence> evidence = items.stream()
                .map(item -> new ConversationArchiveEvidence(
                        "archive:" + item.getArchiveItemId(),
                        item.getArchiveItemId(),
                        bounded(localizedTitle(item.getTitleBg(), item.getTitleEn(), language), MAXIMUM_ARCHIVE_FACT_CHARACTERS),
                        bounded(localizedTitle(
                                item.getDescriptionBg(),
                                item.getDescriptionEn(),
                                language
                        ), MAXIMUM_ARCHIVE_DESCRIPTION_CHARACTERS),
                        item.getArchiveType(),
                        bounded(item.getPeriodText(), MAXIMUM_ARCHIVE_FACT_CHARACTERS),
                        bounded(item.getOriginText(), MAXIMUM_ARCHIVE_FACT_CHARACTERS),
                        bounded(item.getCurrentLocation(), MAXIMUM_ARCHIVE_FACT_CHARACTERS),
                        item.getTrustedLevel(),
                        item.getSourceReferenceId()
                ))
                .toList();

        return new ConversationArchiveEvidenceSelection(evidence, cards);
    }

    private String localizedTitle(String titleBg, String titleEn, String language) {
        return "en".equalsIgnoreCase(language)
                ? TextUtils.defaultIfBlank(titleEn, titleBg)
                : TextUtils.defaultIfBlank(titleBg, titleEn);
    }

    private String bounded(String value, int maximumCharacters) {
        if (value == null || value.length() <= maximumCharacters)
            return value;

        return value.substring(0, maximumCharacters);
    }
}
