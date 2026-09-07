package fmi.ethnowear.infrastructure.ontology.jena;

import fmi.ethnowear.application.model.ontology.OntologyChangeMetadata;
import fmi.ethnowear.application.model.ontology.OntologySnapshot;
import fmi.ethnowear.application.port.ontology.admin.OntologyChangeMetadataProvider;
import fmi.ethnowear.application.port.ontology.admin.OntologyVersionRecorder;
import fmi.ethnowear.util.ContentHashUtils;
import fmi.ethnowear.util.ProjectPathResolver;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.reasoner.ValidityReport;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.springframework.cache.annotation.CacheEvict;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

import static fmi.ethnowear.application.constant.CacheNames.ONTOLOGY_REFERENCE_FULL;

public class JenaOntologyStore {

    private static final Logger log = LoggerFactory.getLogger(JenaOntologyStore.class);

    private final Path ontologyPath;
    private final String namespace;
    private final OntologyVersionRecorder versionRecorder;
    private final OntologyChangeMetadataProvider metadataProvider;
    /*
     * Jena's in-memory model and rule reasoner are not safe for concurrent API
     * operations. Keep the complete callback inside one fair critical section;
     * callers must also fully materialize results before returning from it.
     */
    private final ReentrantLock modelLock = new ReentrantLock(true);

    private OntModel assertedModel;
    private OntModel inferenceModel;

    public JenaOntologyStore(Path path, String namespace) {
        this(
                path,
                namespace,
                OntologyVersionRecorder.disabled(),
                OntologyChangeMetadataProvider.systemDefault()
        );
    }

    public JenaOntologyStore(
            Path path,
            String namespace,
            OntologyVersionRecorder versionRecorder,
            OntologyChangeMetadataProvider metadataProvider
    ) {
        this.ontologyPath = ProjectPathResolver.resolve(path);
        this.namespace = namespace;
        this.versionRecorder = versionRecorder;
        this.metadataProvider = metadataProvider;
        reload();
    }

    public <T> T read(Function<OntModel, T> operation) {
        modelLock.lock();
        try {
            return operation.apply(inferenceModel);
        } finally {
            modelLock.unlock();
        }
    }

    @CacheEvict(cacheNames = ONTOLOGY_REFERENCE_FULL, allEntries = true)
    public <T> T write(Function<OntModel, T> operation) {
        modelLock.lock();
        try {
            OntologyChangeMetadata metadata = metadataProvider.current();
            OntologySnapshot previousSnapshot = fileSnapshot();
            Long previousVersionId = versionRecorder.ensureActiveSnapshot(previousSnapshot, metadata);
            OntModel working = copy(assertedModel);
            OntModel prepared = null;
            OntologySnapshot candidate = null;
            Long stagedVersionId = null;
            boolean persistenceAttempted = false;
            boolean valid = false;
            try {
                T result = operation.apply(working);
                candidate = modelSnapshot(working);
                prepared = prepareValidated(candidate);
                valid = true;
                stagedVersionId = versionRecorder.stageSnapshot(
                        previousVersionId,
                        null,
                        candidate,
                        metadata
                );
                persistenceAttempted = true;
                persist(candidate.content());
                versionRecorder.activateSnapshot(
                        stagedVersionId,
                        previousVersionId
                );
                install(prepared);
                prepared = null;
                return result;
            } catch (RuntimeException ex) {
                if (persistenceAttempted)
                    restoreFile(previousSnapshot.content(), ex);
                recordFailure(previousVersionId, stagedVersionId, candidate, metadata, valid, ex);
                throw ex;
            } finally {
                working.close();
                if (prepared != null)
                    prepared.close();
            }
        } finally {
            modelLock.unlock();
        }
    }

    @CacheEvict(cacheNames = ONTOLOGY_REFERENCE_FULL, allEntries = true)
    public void reload() {
        modelLock.lock();
        try {
            if (!Files.isRegularFile(ontologyPath)) {
                throw new IllegalStateException("Ontology file not found: " + ontologyPath);
            }

            try {
                install(prepareValidated(snapshot(Files.readString(ontologyPath, StandardCharsets.UTF_8))));
            } catch (Exception ex) {
                throw new IllegalStateException("Could not load ontology", ex);
            }

        } finally {
            modelLock.unlock();
        }
    }

    @CacheEvict(cacheNames = ONTOLOGY_REFERENCE_FULL, allEntries = true)
    public Long restore(
            OntologySnapshot selectedSnapshot,
            Long restoredFromVersionId,
            OntologyChangeMetadata metadata
    ) {
        modelLock.lock();
        try {
            requireCompatible(selectedSnapshot);
            OntologySnapshot previousSnapshot = fileSnapshot();
            Long previousVersionId = versionRecorder.ensureActiveSnapshot(previousSnapshot, metadata);
            Long stagedVersionId = null;
            OntModel prepared = null;
            boolean persistenceAttempted = false;
            boolean valid = false;

            try {
                prepared = prepareValidated(selectedSnapshot);
                valid = true;
                stagedVersionId = versionRecorder.stageSnapshot(
                        previousVersionId,
                        restoredFromVersionId,
                        selectedSnapshot,
                        metadata
                );
                persistenceAttempted = true;
                persist(selectedSnapshot.content());
                Long versionId = versionRecorder.activateSnapshot(
                        stagedVersionId,
                        previousVersionId
                );
                install(prepared);
                prepared = null;
                return versionId;
            } catch (RuntimeException ex) {
                if (persistenceAttempted)
                    restoreFile(previousSnapshot.content(), ex);
                recordFailure(previousVersionId, stagedVersionId, selectedSnapshot, metadata, valid, ex);
                throw ex;
            } finally {
                if (prepared != null)
                    prepared.close();
            }
        } finally {
            modelLock.unlock();
        }
    }

    private void persist(String content) {
        Path temp = ontologyPath.resolveSibling(ontologyPath.getFileName() + ".tmp");
        Path backup = ontologyPath.resolveSibling(ontologyPath.getFileName() + ".bak");

        try {
            Files.writeString(temp, content, StandardCharsets.UTF_8);
            // The candidate was already validated; verify the exact persisted bytes.
            if (!content.equals(Files.readString(temp, StandardCharsets.UTF_8)))
                throw new IllegalStateException("Ontology temporary file differs from validated content");

            Files.copy(ontologyPath, backup, StandardCopyOption.REPLACE_EXISTING);
            try {
                Files.move(
                        temp,
                        ontologyPath,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE
                );
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temp, ontologyPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Could not persist ontology", ex);
        } finally {
            try {
                Files.deleteIfExists(temp);
            } catch (Exception ignored) {
                // The original persistence exception, if any, is more useful to callers.
            }
        }
    }

    private void install(OntModel prepared) {
        OntModel previous = inferenceModel;
        assertedModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_MEM, prepared.getBaseModel());
        inferenceModel = prepared;
        if (previous != null) {
            try {
                previous.close();
            } catch (RuntimeException ex) {
                // Cleanup must not roll back an already activated SQL/file version.
                log.warn("Could not close superseded ontology model", ex);
            }
        }
    }

    private OntologySnapshot fileSnapshot() {
        try {
            return snapshot(Files.readString(ontologyPath, StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("Could not read ontology snapshot", ex);
        }
    }

    private OntologySnapshot modelSnapshot(OntModel model) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            RDFDataMgr.write(output, model, RDFFormat.RDFXML_PRETTY);
            return snapshot(output.toString(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("Could not serialize ontology", ex);
        }
    }

    private OntologySnapshot snapshot(String content) {
        return new OntologySnapshot(
                content,
                ContentHashUtils.sha256(content),
                ontologyPath.getFileName().toString(),
                namespace
        );
    }

    private OntModel parse(String content) {
        OntModel model = ModelFactory.createOntologyModel(OntModelSpec.OWL_MEM);
        try (InputStream input = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8))) {
            RDFDataMgr.read(model, input, Lang.RDFXML);
            return model;
        } catch (Exception ex) {
            model.close();
            throw new IllegalArgumentException("Ontology content is not valid RDF/XML", ex);
        }
    }

    private OntModel prepareValidated(OntologySnapshot snapshot) {
        if (!ContentHashUtils.sha256(snapshot.content()).equals(snapshot.contentHash()))
            throw new IllegalArgumentException("Ontology content hash does not match the snapshot");

        OntModel verification = ModelFactory.createOntologyModel(
                OntModelSpec.OWL_MEM_RULE_INF,
                parse(snapshot.content())
        );
        try {
            verification.prepare();
            ValidityReport report = verification.validate();
            if (!report.isValid())
                throw new IllegalArgumentException("Ontology validation failed");
            return verification;
        } catch (RuntimeException ex) {
            verification.close();
            throw ex;
        }
    }

    private void requireCompatible(OntologySnapshot snapshot) {
        if (snapshot == null)
            throw new IllegalArgumentException("Ontology version snapshot is required");
        if (!namespace.equals(snapshot.namespace()))
            throw new IllegalArgumentException("Ontology version namespace does not match the active ontology");
        if (!ontologyPath.getFileName().toString().equals(snapshot.fileName()))
            throw new IllegalArgumentException("Ontology version file name does not match the active ontology");
    }

    private void restoreFile(String content, RuntimeException original) {
        try {
            persist(content);
        } catch (RuntimeException restoreFailure) {
            original.addSuppressed(restoreFailure);
        }
    }

    private void recordFailure(
            Long previousVersionId,
            Long stagedVersionId,
            OntologySnapshot candidate,
            OntologyChangeMetadata metadata,
            boolean valid,
            RuntimeException failure
    ) {
        if (candidate == null)
            return;

        try {
            if (stagedVersionId != null) {
                versionRecorder.failStagedSnapshot(stagedVersionId, safeMessage(failure));
                return;
            }

            versionRecorder.recordFailedSnapshot(
                    previousVersionId,
                    candidate,
                    metadata,
                    valid,
                    safeMessage(failure)
            );
        } catch (RuntimeException historyFailure) {
            failure.addSuppressed(historyFailure);
        }
    }

    private String safeMessage(RuntimeException failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank()
                ? "Ontology activation failed"
                : message;
    }

    private OntModel copy(OntModel source) {
        OntModel copy = ModelFactory.createOntologyModel(OntModelSpec.OWL_MEM);
        copy.add(source.getBaseModel());
        copy.setNsPrefixes(source.getNsPrefixMap());
        return copy;
    }

    public String uri(String localName) {
        return namespace + localName;
    }

}
