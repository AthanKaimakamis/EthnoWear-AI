package fmi.ethnowear.infrastructure.ontology.jena;

import fmi.ethnowear.application.model.ontology.OntologyChangeMetadata;
import fmi.ethnowear.application.model.ontology.OntologySnapshot;
import fmi.ethnowear.application.port.ontology.admin.OntologyVersionRecorder;
import fmi.ethnowear.util.ContentHashUtils;
import org.apache.jena.rdf.model.ResourceFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JenaOntologyStoreVersioningTest {

    private static final String NAMESPACE = "https://example.test/ontology#";
    private static final String ONTOLOGY = """
            <?xml version="1.0"?>
            <rdf:RDF
                xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
                xmlns:owl="http://www.w3.org/2002/07/owl#">
              <owl:Ontology rdf:about="https://example.test/ontology"/>
              <owl:Class rdf:about="https://example.test/ontology#Example"/>
            </rdf:RDF>
            """;

    @TempDir
    Path tempDirectory;

    @Test
    void recordsBaselineStagesCandidateAndActivatesAfterPersistence() throws Exception {
        RecordingVersionRecorder recorder = new RecordingVersionRecorder();
        Path path = ontologyPath();
        JenaOntologyStore store = store(path, recorder);

        store.write(model -> {
            model.add(
                    model.getResource(store.uri("Example")),
                    ResourceFactory.createProperty(store.uri("relatedTo")),
                    model.getResource(store.uri("Other"))
            );
            return null;
        });

        assertEquals(1, recorder.ensured.size());
        assertEquals(1, recorder.staged.size());
        assertEquals(List.of(2L), recorder.activated);
        assertEquals(
                ContentHashUtils.sha256(Files.readString(path, StandardCharsets.UTF_8)),
                recorder.staged.getFirst().contentHash()
        );
        assertNotEquals(recorder.ensured.getFirst().contentHash(), recorder.staged.getFirst().contentHash());
        assertTrue(Files.isRegularFile(path.resolveSibling("ontology.owl.bak")));
    }

    @Test
    void restoresFileAndMarksStagedVersionFailedWhenSqlActivationFails() throws Exception {
        RecordingVersionRecorder recorder = new RecordingVersionRecorder();
        recorder.failActivation = true;
        Path path = ontologyPath();
        String original = Files.readString(path, StandardCharsets.UTF_8);
        JenaOntologyStore store = store(path, recorder);

        assertThrows(IllegalStateException.class, () -> store.write(model -> {
            model.createClass(store.uri("Added"));
            return null;
        }));

        assertEquals(original, Files.readString(path, StandardCharsets.UTF_8));
        assertEquals(List.of(2L), recorder.failedStaged);
        assertEquals(1, store.<Integer>read(model -> model.listClasses()
                .filterKeep(resource -> store.uri("Example").equals(resource.getURI()))
                .toList()
                .size()));
    }

    @Test
    void restoreCreatesNewVersionLinkedToSelectedHistory() throws Exception {
        RecordingVersionRecorder recorder = new RecordingVersionRecorder();
        Path path = ontologyPath();
        JenaOntologyStore store = store(path, recorder);
        OntologySnapshot selected = new OntologySnapshot(
                ONTOLOGY,
                ContentHashUtils.sha256(ONTOLOGY),
                path.getFileName().toString(),
                NAMESPACE
        );

        Long restoredVersionId = store.restore(
                selected,
                41L,
                new OntologyChangeMetadata(7L, "Restore known version")
        );

        assertEquals(2L, restoredVersionId);
        assertEquals(List.of(41L), recorder.restoredFrom);
        assertEquals(ONTOLOGY, Files.readString(path, StandardCharsets.UTF_8));
    }

    @Test
    void rejectsSnapshotWhoseHashDoesNotMatchContent() throws Exception {
        RecordingVersionRecorder recorder = new RecordingVersionRecorder();
        Path path = ontologyPath();
        JenaOntologyStore store = store(path, recorder);
        OntologySnapshot invalid = new OntologySnapshot(
                ONTOLOGY,
                "0".repeat(64),
                path.getFileName().toString(),
                NAMESPACE
        );

        assertThrows(IllegalArgumentException.class, () -> store.restore(
                invalid,
                1L,
                new OntologyChangeMetadata(7L, "Invalid restore")
        ));
        assertTrue(recorder.staged.isEmpty());
    }

    private Path ontologyPath() throws Exception {
        Path path = tempDirectory.resolve("ontology.owl");
        Files.writeString(path, ONTOLOGY, StandardCharsets.UTF_8);
        return path;
    }

    private JenaOntologyStore store(Path path, OntologyVersionRecorder recorder) {
        return new JenaOntologyStore(
                path,
                NAMESPACE,
                recorder,
                () -> new OntologyChangeMetadata(7L, "Test ontology change")
        );
    }

    private static final class RecordingVersionRecorder implements OntologyVersionRecorder {
        private final List<OntologySnapshot> ensured = new ArrayList<>();
        private final List<OntologySnapshot> staged = new ArrayList<>();
        private final List<Long> restoredFrom = new ArrayList<>();
        private final List<Long> activated = new ArrayList<>();
        private final List<Long> failedStaged = new ArrayList<>();
        private boolean failActivation;

        @Override
        public Long ensureActiveSnapshot(OntologySnapshot snapshot, OntologyChangeMetadata metadata) {
            ensured.add(snapshot);
            return 1L;
        }

        @Override
        public Long stageSnapshot(Long previousVersionId, Long restoredFromVersionId,
                                  OntologySnapshot snapshot, OntologyChangeMetadata metadata) {
            assertEquals(1L, previousVersionId);
            staged.add(snapshot);
            if (restoredFromVersionId != null)
                restoredFrom.add(restoredFromVersionId);
            return 2L;
        }

        @Override
        public Long activateSnapshot(Long stagedVersionId, Long previousVersionId) {
            if (failActivation)
                throw new IllegalStateException("Simulated SQL activation failure");
            activated.add(stagedVersionId);
            return stagedVersionId;
        }

        @Override
        public void failStagedSnapshot(Long stagedVersionId, String validationMessage) {
            failedStaged.add(stagedVersionId);
        }

        @Override
        public void recordFailedSnapshot(Long previousVersionId, OntologySnapshot snapshot,
                                         OntologyChangeMetadata metadata, boolean valid,
                                         String validationMessage) {
        }
    }
}
