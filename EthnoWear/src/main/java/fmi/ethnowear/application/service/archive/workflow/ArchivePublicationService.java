package fmi.ethnowear.application.service.archive.workflow;

import fmi.ethnowear.application.dto.archive.item.ArchiveItemDetails;
import fmi.ethnowear.application.dto.archive.workflow.ArchivePublicationCheckDetails;
import fmi.ethnowear.application.dto.archive.workflow.ArchivePublicationReadinessDetails;
import fmi.ethnowear.application.exception.ArchiveNotReadyForPublicationException;
import fmi.ethnowear.application.exception.InvalidPublicationTransitionException;
import fmi.ethnowear.application.exception.ResourceNotFoundException;
import fmi.ethnowear.application.service.archive.item.ArchiveItemMapper;
import fmi.ethnowear.domain.model.archive.ArchivePublicationRequirement;
import fmi.ethnowear.domain.model.archive.PublicationStatus;
import fmi.ethnowear.persistence.jpa.entity.ArchiveItem;
import fmi.ethnowear.persistence.jpa.repository.ArchiveItemRepository;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static fmi.ethnowear.util.IdentifierUtils.requireId;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ArchivePublicationService {

    private final ArchiveItemRepository archiveItemRepository;
    private final ArchivePublicationValidator publicationValidator;
    private final ArchiveItemMapper archiveItemMapper;

    public ArchivePublicationReadinessDetails validate(Long archiveItemId) {
        return publicationValidator.validate(archiveItemId);
    }

    @Transactional
    public ArchiveItemDetails submit(Long archiveItemId) {
        ArchiveItem item = requireItem(archiveItemId);

        requireStatus(item, PublicationStatus.DRAFT, PublicationStatus.IN_REVIEW);
        requireReady(archiveItemId);

        item.setPublicationStatus(PublicationStatus.IN_REVIEW);
        item.setSubmittedAt(LocalDateTime.now());
        item.setPublishedAt(null);
        item.setArchivedAt(null);

        return save(item);
    }

    @Transactional
    public ArchiveItemDetails returnToDraft(Long archiveItemId) {
        ArchiveItem item = requireItem(archiveItemId);

        requireStatus(item, PublicationStatus.IN_REVIEW, PublicationStatus.DRAFT);

        item.setPublicationStatus(PublicationStatus.DRAFT);
        item.setSubmittedAt(null);
        item.setPublishedAt(null);
        item.setArchivedAt(null);

        return save(item);
    }

    @Transactional
    public ArchiveItemDetails publish(Long archiveItemId) {
        ArchiveItem item = requireItem(archiveItemId);

        requireStatus(item, PublicationStatus.IN_REVIEW, PublicationStatus.PUBLISHED);
        requireReady(archiveItemId);

        item.setPublicationStatus(PublicationStatus.PUBLISHED);
        item.setPublishedAt(LocalDateTime.now());
        item.setArchivedAt(null);

        return save(item);
    }

    @Transactional
    public ArchiveItemDetails archive(Long archiveItemId) {
        ArchiveItem item = requireItem(archiveItemId);

        requireStatus(item, PublicationStatus.PUBLISHED, PublicationStatus.ARCHIVED);

        item.setPublicationStatus(PublicationStatus.ARCHIVED);
        item.setArchivedAt(LocalDateTime.now());

        return save(item);
    }

    private void requireReady(Long archiveItemId) {
        ArchivePublicationReadinessDetails readiness = publicationValidator.validate(archiveItemId);

        if(readiness.ready())
            return;

        List<ArchivePublicationRequirement> failedRequirements = readiness
                .checks()
                .stream()
                .filter(check -> check.blocking() && !check.satisfied())
                .map(ArchivePublicationCheckDetails::requirement)
                .toList();

        throw new ArchiveNotReadyForPublicationException(archiveItemId, failedRequirements);
    }

    private void requireStatus(
            @NonNull ArchiveItem item,
            PublicationStatus expectedStatus,
            PublicationStatus targetStatus
    ) {
        if (item.getPublicationStatus() != expectedStatus)
            throw new InvalidPublicationTransitionException(
                    item.getId(),
                    item.getPublicationStatus(),
                    targetStatus
            );
    }

    private @NonNull ArchiveItem requireItem(Long archiveItemId) {
        requireId(archiveItemId, "Archive item");

        return archiveItemRepository
                .findById(archiveItemId)
                .orElseThrow(() -> new ResourceNotFoundException("Archive item", archiveItemId));
    }

    private ArchiveItemDetails save(ArchiveItem item) {
        return archiveItemMapper.toDetails(archiveItemRepository.save(item));
    }
}
