package fmi.ethnowear.application.service.ontology.version;

import fmi.ethnowear.application.model.ontology.OntologyChangeMetadata;
import fmi.ethnowear.application.model.ontology.OntologySnapshot;
import fmi.ethnowear.application.port.ontology.admin.OntologyVersionRecorder;
import fmi.ethnowear.domain.model.ontology.OntologyVersionStatus;
import fmi.ethnowear.persistence.jpa.entity.ontology.OntologyVersion;
import fmi.ethnowear.persistence.jpa.entity.user.User;
import fmi.ethnowear.persistence.jpa.repository.ontology.OntologyVersionRepository;
import fmi.ethnowear.persistence.jpa.repository.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class JpaOntologyVersionRecorder implements OntologyVersionRecorder {

    private static final int MAXIMUM_REASON_LENGTH = 500;
    private static final int MAXIMUM_VALIDATION_MESSAGE_LENGTH = 2000;

    private final OntologyVersionRepository versionRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long ensureActiveSnapshot(OntologySnapshot snapshot, OntologyChangeMetadata metadata) {
        OntologyVersion active = versionRepository
                .findByStatusForUpdate(OntologyVersionStatus.ACTIVE)
                .orElse(null);

        if (active != null && active.getContentHash().equals(snapshot.contentHash()))
            return active.getId();

        if (active != null) {
            active.supersede();
            versionRepository.flush();
        }

        OntologyVersion baseline = newVersion(
                active,
                null,
                snapshot,
                metadata,
                true,
                null,
                OntologyVersionStatus.ACTIVE,
                active == null
                        ? "Initial ontology snapshot before administration change"
                        : "Reconciled ontology file before administration change: " + metadata.reason()
        );

        return versionRepository.saveAndFlush(baseline).getId();
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long stageSnapshot(
            Long previousVersionId,
            Long restoredFromVersionId,
            OntologySnapshot snapshot,
            OntologyChangeMetadata metadata
    ) {
        OntologyVersion active = requiredActive(previousVersionId);
        OntologyVersion restoredFrom = restoredFromVersionId == null
                ? null
                : versionRepository.findById(restoredFromVersionId)
                .orElseThrow(() -> new IllegalStateException("Ontology restore source version no longer exists"));

        OntologyVersion version = newVersion(
                active,
                restoredFrom,
                snapshot,
                metadata,
                true,
                null,
                OntologyVersionStatus.STAGED,
                metadata.reason()
        );

        return versionRepository.saveAndFlush(version).getId();
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long activateSnapshot(Long stagedVersionId, Long previousVersionId) {
        OntologyVersion active = requiredActive(previousVersionId);
        OntologyVersion staged = versionRepository.findById(stagedVersionId)
                .orElseThrow(() -> new IllegalStateException("Staged ontology version is missing"));

        active.supersede();
        versionRepository.flush();
        staged.activate();
        versionRepository.flush();
        return staged.getId();
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failStagedSnapshot(Long stagedVersionId, String validationMessage) {
        OntologyVersion staged = versionRepository.findById(stagedVersionId)
                .orElse(null);
        if (staged == null || staged.getStatus() != OntologyVersionStatus.STAGED)
            return;

        staged.failActivation(normalize(
                validationMessage,
                MAXIMUM_VALIDATION_MESSAGE_LENGTH,
                "Ontology activation failed"
        ));
        versionRepository.flush();
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailedSnapshot(
            Long previousVersionId,
            OntologySnapshot snapshot,
            OntologyChangeMetadata metadata,
            boolean valid,
            String validationMessage
    ) {
        OntologyVersion previous = previousVersionId == null
                ? null
                : versionRepository.findById(previousVersionId).orElse(null);

        versionRepository.saveAndFlush(newVersion(
                previous,
                null,
                snapshot,
                metadata,
                valid,
                normalize(validationMessage, MAXIMUM_VALIDATION_MESSAGE_LENGTH, "Ontology activation failed"),
                OntologyVersionStatus.FAILED,
                metadata.reason()
        ));
    }

    private OntologyVersion requiredActive(Long expectedId) {
        OntologyVersion active = versionRepository
                .findByStatusForUpdate(OntologyVersionStatus.ACTIVE)
                .orElseThrow(() -> new IllegalStateException("Active ontology version is missing"));

        if (expectedId == null || !active.getId().equals(expectedId))
            throw new IllegalStateException("Active ontology version changed during update");

        return active;
    }

    private OntologyVersion newVersion(
            OntologyVersion previous,
            OntologyVersion restoredFrom,
            OntologySnapshot snapshot,
            OntologyChangeMetadata metadata,
            boolean valid,
            String validationMessage,
            OntologyVersionStatus status,
            String reason
    ) {
        User actor = metadata.userId() == null
                ? null
                : userRepository.getReferenceById(metadata.userId());

        return new OntologyVersion(
                versionRepository.maximumVersionNumber() + 1,
                previous,
                restoredFrom,
                actor,
                normalize(reason, MAXIMUM_REASON_LENGTH, "Ontology change"),
                snapshot.content(),
                snapshot.contentHash(),
                snapshot.fileName(),
                snapshot.namespace(),
                valid,
                validationMessage,
                status
        );
    }

    private static String normalize(String value, int maximumLength, String fallback) {
        String normalized = value == null || value.isBlank() ? fallback : value.trim();
        return normalized.length() <= maximumLength
                ? normalized
                : normalized.substring(0, maximumLength);
    }
}
