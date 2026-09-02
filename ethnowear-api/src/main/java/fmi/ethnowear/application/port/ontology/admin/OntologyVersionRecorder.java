package fmi.ethnowear.application.port.ontology.admin;

import fmi.ethnowear.application.model.ontology.OntologyChangeMetadata;
import fmi.ethnowear.application.model.ontology.OntologySnapshot;

public interface OntologyVersionRecorder {

    Long ensureActiveSnapshot(OntologySnapshot snapshot, OntologyChangeMetadata metadata);

    Long stageSnapshot(
            Long previousVersionId,
            Long restoredFromVersionId,
            OntologySnapshot snapshot,
            OntologyChangeMetadata metadata
    );

    Long activateSnapshot(Long stagedVersionId, Long previousVersionId);

    void failStagedSnapshot(Long stagedVersionId, String validationMessage);

    void recordFailedSnapshot(
            Long previousVersionId,
            OntologySnapshot snapshot,
            OntologyChangeMetadata metadata,
            boolean valid,
            String validationMessage
    );

    static OntologyVersionRecorder disabled() {
        return new OntologyVersionRecorder() {
            @Override
            public Long ensureActiveSnapshot(OntologySnapshot snapshot, OntologyChangeMetadata metadata) {
                return null;
            }

            @Override
            public Long stageSnapshot(Long previousVersionId, Long restoredFromVersionId,
                                      OntologySnapshot snapshot, OntologyChangeMetadata metadata) {
                return null;
            }

            @Override
            public Long activateSnapshot(Long stagedVersionId, Long previousVersionId) {
                return stagedVersionId;
            }

            @Override
            public void failStagedSnapshot(Long stagedVersionId, String validationMessage) {
            }

            @Override
            public void recordFailedSnapshot(Long previousVersionId, OntologySnapshot snapshot,
                                             OntologyChangeMetadata metadata, boolean valid,
                                             String validationMessage) {
            }
        };
    }
}
