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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

import static fmi.ethnowear.application.constant.CacheNames.ONTOLOGY_REFERENCE_FULL;

public class JenaOntologyStore {

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
            OntModel backup = copy(assertedModel);
            OntologySnapshot candidate = null;
            Long stagedVersionId = null;
            try {
                T result = operation.apply(assertedModel);
                candidate = modelSnapshot(assertedModel);
                validate(candidate);
                stagedVersionId = versionRecorder.stageSnapshot(
                        previousVersionId,
                        null,
                        candidate,
                        metadata
                );
                persist(candidate.content());
                rebuildInferenceModel();
                versionRecorder.activateSnapshot(
                        stagedVersionId,
                        previousVersionId
                );
                return result;
            } catch (RuntimeException ex) {
                assertedModel = backup;
                restoreFile(previousSnapshot.content(), ex);
                rebuildInferenceModel();
                recordFailure(previousVersionId, stagedVersionId, candidate, metadata, ex);
                throw ex;
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
                assertedModel = parse(Files.readString(ontologyPath, StandardCharsets.UTF_8));
            } catch (Exception ex) {
                throw new IllegalStateException("Could not load ontology", ex);
            }

            rebuildInferenceModel();
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

            try {
                validate(selectedSnapshot);
                stagedVersionId = versionRecorder.stageSnapshot(
                        previousVersionId,
                        restoredFromVersionId,
                        selectedSnapshot,
                        metadata
                );
                persist(selectedSnapshot.content());
                assertedModel = parse(selectedSnapshot.content());
                rebuildInferenceModel();
                return versionRecorder.activateSnapshot(
                        stagedVersionId,
                        previousVersionId
                );
            } catch (RuntimeException ex) {
                restoreFile(previousSnapshot.content(), ex);
                assertedModel = parse(previousSnapshot.content());
                rebuildInferenceModel();
                recordFailure(previousVersionId, stagedVersionId, selectedSnapshot, metadata, ex);
                throw ex;
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
            validate(snapshot(Files.readString(temp, StandardCharsets.UTF_8)));

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

    private void rebuildInferenceModel() {
        inferenceModel = ModelFactory.createOntologyModel(
                OntModelSpec.OWL_MEM_RULE_INF,
                assertedModel
        );
        inferenceModel.prepare();
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
            throw new IllegalArgumentException("Ontology content is not valid RDF/XML", ex);
        }
    }

    private void validate(OntologySnapshot snapshot) {
        if (!ContentHashUtils.sha256(snapshot.content()).equals(snapshot.contentHash()))
            throw new IllegalArgumentException("Ontology content hash does not match the snapshot");

        OntModel verification = ModelFactory.createOntologyModel(
                OntModelSpec.OWL_MEM_RULE_INF,
                parse(snapshot.content())
        );
        verification.prepare();
        ValidityReport report = verification.validate();
        if (!report.isValid())
            throw new IllegalArgumentException("Ontology validation failed");
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
            RuntimeException failure
    ) {
        if (candidate == null)
            return;

        try {
            if (stagedVersionId != null) {
                versionRecorder.failStagedSnapshot(stagedVersionId, safeMessage(failure));
                return;
            }

            boolean valid = isValid(candidate);
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

    private boolean isValid(OntologySnapshot snapshot) {
        try {
            validate(snapshot);
            return true;
        } catch (RuntimeException ignored) {
            return false;
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
