package fmi.ethnowear.application.service.archive.workflow;

import fmi.ethnowear.application.dto.archive.workflow.ArchivePublicationCheckDetails;
import fmi.ethnowear.application.dto.archive.workflow.ArchivePublicationReadinessDetails;
import fmi.ethnowear.application.exception.ResourceNotFoundException;
import fmi.ethnowear.domain.model.archive.ArchivePublicationRequirement;
import fmi.ethnowear.domain.model.archive.MediaRole;
import fmi.ethnowear.domain.model.ontology.OntologyIdentity;
import fmi.ethnowear.persistence.jpa.entity.ArchiveItem;
import fmi.ethnowear.persistence.jpa.repository.ArchiveItemFeatureRepository;
import fmi.ethnowear.persistence.jpa.repository.ArchiveItemMediaRepository;
import fmi.ethnowear.persistence.jpa.repository.ArchiveItemRepository;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static fmi.ethnowear.util.TextUtils.isBlank;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ArchivePublicationValidator {

    private final ArchiveItemRepository archiveItemRepository;
    private final ArchiveItemFeatureRepository featureRepository;
    private final ArchiveItemMediaRepository mediaRepository;

    public ArchivePublicationReadinessDetails validate(Long archiveItemId) {
        ArchiveItem item = archiveItemRepository
                .findById(archiveItemId)
                .orElseThrow(() -> new ResourceNotFoundException("Archive item", archiveItemId));

        List<ArchivePublicationCheckDetails> checks = List.of(
                check(
                        ArchivePublicationRequirement.LOCALIZED_TITLE,
                        !isBlank(item.getTitleBg()) || !isBlank(item.getTitleEn()),
                        true
                ),
                check(
                        ArchivePublicationRequirement.SOURCE_REFERENCE,
                        item.getSourceReference() != null,
                        true
                ),
                check(
                        ArchivePublicationRequirement.ONTOLOGY_CLASSIFICATION,
                        hasClassification(item),
                        true
                ),
                check(
                        ArchivePublicationRequirement.FEATURES_VALIDATED,
                        !featureRepository.existsByArchiveItem_IdAndValidatedFalse(archiveItemId),
                        true
                ),
                check(
                        ArchivePublicationRequirement.MEDIA_ATTACHED,
                        mediaRepository.existsByArchiveItem_Id(archiveItemId),
                        false
                ),
                check(
                        ArchivePublicationRequirement.PRIMARY_MEDIA,
                        mediaRepository.existsByArchiveItem_IdAndRole(
                                archiveItemId,
                                MediaRole.PRIMARY
                        ),
                        false
                )
        );

        boolean ready = checks.stream()
                .filter(ArchivePublicationCheckDetails::blocking)
                .allMatch(ArchivePublicationCheckDetails::satisfied);

        return new ArchivePublicationReadinessDetails(
                item.getId(),
                item.getPublicationStatus(),
                ready,
                checks
        );
    }

    private boolean hasClassification(@NonNull ArchiveItem item) {
        return new OntologyIdentity(
                item.getOntologyRegionIri(),
                item.getOntologyRegionLocalName()
        ).isComplete() || new OntologyIdentity(
                item.getOntologyRegionalEmbroideryIri(),
                item.getOntologyRegionalEmbroideryLocalName()
        ).isComplete();
    }

    @Contract("_, _, _ -> new")
    private @NonNull ArchivePublicationCheckDetails check(
            ArchivePublicationRequirement requirement,
            boolean satisfied,
            boolean blocking
    ) {
        return new ArchivePublicationCheckDetails(
                requirement,
                satisfied,
                blocking
        );
    }
}
