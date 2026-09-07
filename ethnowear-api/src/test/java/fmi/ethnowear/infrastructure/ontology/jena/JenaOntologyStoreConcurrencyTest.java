package fmi.ethnowear.infrastructure.ontology.jena;

import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.vocabulary.RDF;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JenaOntologyStoreConcurrencyTest {

    private static final String NAMESPACE = "https://example.test/ontology#";
    private static final String ONTOLOGY = """
            <?xml version="1.0"?>
            <rdf:RDF
                xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
                xmlns:rdfs="http://www.w3.org/2000/01/rdf-schema#"
                xmlns:owl="http://www.w3.org/2002/07/owl#"
                xmlns:test="https://example.test/ontology#">
              <owl:Ontology rdf:about="https://example.test/ontology"/>
              <owl:Class rdf:about="https://example.test/ontology#Parent"/>
              <owl:Class rdf:about="https://example.test/ontology#Child">
                <rdfs:subClassOf rdf:resource="https://example.test/ontology#Parent"/>
              </owl:Class>
              <owl:NamedIndividual rdf:about="https://example.test/ontology#Example">
                <rdf:type rdf:resource="https://example.test/ontology#Child"/>
              </owl:NamedIndividual>
            </rdf:RDF>
            """;

    @TempDir
    Path tempDirectory;

    @Test
    void serializesConcurrentReadCallbacks() throws Exception {
        JenaOntologyStore store = store();
        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        AtomicBoolean secondEntered = new AtomicBoolean();

        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<Void> first = executor.submit(() -> store.read(model -> {
                firstEntered.countDown();
                await(releaseFirst);
                return null;
            }));

            assertTrue(firstEntered.await(2, TimeUnit.SECONDS));

            Future<Void> second = executor.submit(() -> {
                secondStarted.countDown();
                return store.read(model -> {
                    secondEntered.set(true);
                    return null;
                });
            });

            assertTrue(secondStarted.await(2, TimeUnit.SECONDS));
            Thread.sleep(100);
            assertFalse(secondEntered.get());

            releaseFirst.countDown();
            first.get(2, TimeUnit.SECONDS);
            second.get(2, TimeUnit.SECONDS);
            assertTrue(secondEntered.get());
        }
    }

    @Test
    void repeatedConcurrentInferenceReadsRemainStable() throws Exception {
        JenaOntologyStore store = store();

        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            try (var executor = Executors.newFixedThreadPool(8)) {
                List<Future<Integer>> reads = new ArrayList<>();
                for (int index = 0; index < 200; index++) {
                    reads.add(executor.submit(() -> store.read(model ->
                            model.listIndividuals(model.getOntClass(store.uri("Parent")))
                                    .toList()
                                    .size()
                    )));
                }

                for (Future<Integer> read : reads) {
                    assertEquals(1, read.get(5, TimeUnit.SECONDS));
                }
            }
        });
    }

    private JenaOntologyStore store() throws Exception {
        Path ontology = tempDirectory.resolve("ontology.owl");
        Files.writeString(ontology, ONTOLOGY);
        return new JenaOntologyStore(ontology, NAMESPACE);
    }

    @Test
    void writesPublishPreparedInferenceWithoutPersistingDeductions() throws Exception {
        JenaOntologyStore store = store();
        store.write(model -> {
            model.createIndividual(store.uri("Second"), model.getOntClass(store.uri("Child")));
            return null;
        });

        assertEquals(2, store.<Integer>read(model -> model.listIndividuals(
                model.getOntClass(store.uri("Parent"))).toList().size()));
        var persisted = RDFDataMgr.loadModel(
                tempDirectory.resolve("ontology.owl").toUri().toString());
        try {
            assertFalse(persisted.contains(
                    persisted.getResource(store.uri("Second")),
                    RDF.type,
                    persisted.getResource(store.uri("Parent"))));
        } finally {
            persisted.close();
        }
        store.reload();
        assertEquals(2, store.<Integer>read(model -> model.listIndividuals(
                model.getOntClass(store.uri("Parent"))).toList().size()));
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(2, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for test coordination");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for test coordination", exception);
        }
    }
}
