package fmi.ethnowear.infrastructure.ontology.jena;

import fmi.ethnowear.util.ProjectPathResolver;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;

public class JenaOntologyStore {

    private final Path ontologyPath;
    private final String namespace;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    private OntModel assertedModel;
    private OntModel inferenceModel;

    public JenaOntologyStore(Path path, String namespace) {
        this.ontologyPath = ProjectPathResolver.resolve(path);
        this.namespace = namespace;
        reload();
    }

    public <T> T read(Function<OntModel, T> operation) {
        lock.readLock().lock();
        try {
            return operation.apply(inferenceModel);
        } finally {
            lock.readLock().unlock();
        }
    }

    public <T> T write(Function<OntModel, T> operation) {
        lock.writeLock().lock();
        try {
            OntModel backup = copy(assertedModel);
            try {
                T result = operation.apply(assertedModel);
                persist();
                rebuildInferenceModel();
                return result;
            } catch (RuntimeException ex) {
                assertedModel = backup;
                rebuildInferenceModel();
                throw ex;
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void reload() {
        lock.writeLock().lock();
        try {
            if (!Files.isRegularFile(ontologyPath)) {
                throw new IllegalStateException("Ontology file not found: " + ontologyPath);
            }

            assertedModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_MEM);

            try (InputStream input = Files.newInputStream(ontologyPath)) {
                RDFDataMgr.read(assertedModel, input, Lang.RDFXML);
            } catch (Exception ex) {
                throw new IllegalStateException("Could not load ontology", ex);
            }

            rebuildInferenceModel();
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void persist() {
        Path temp = ontologyPath.resolveSibling(ontologyPath.getFileName() + ".tmp");
        Path backup = ontologyPath.resolveSibling(ontologyPath.getFileName() + ".bak");

        try {
            try (OutputStream output = Files.newOutputStream(temp)) {
                RDFDataMgr.write(output, assertedModel, RDFFormat.RDFXML_PRETTY);
            }

            OntModel verification = ModelFactory.createOntologyModel(OntModelSpec.OWL_MEM);
            try (InputStream input = Files.newInputStream(temp)) {
                RDFDataMgr.read(verification, input, Lang.RDFXML);
            }

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
