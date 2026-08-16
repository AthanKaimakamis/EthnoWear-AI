package fmi.ethnowear.application.service.reference;

import com.github.benmanes.caffeine.cache.Caffeine;
import fmi.ethnowear.application.port.ontology.EmbroideryOntologyClient;
import fmi.ethnowear.config.CacheConfig;
import fmi.ethnowear.infrastructure.ontology.jena.JenaOntologyStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static fmi.ethnowear.application.constant.CacheNames.ONTOLOGY_REFERENCE_FULL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OntologyCachingTest {

    private static final String NAMESPACE = "http://example.com/ontology#";

    @TempDir
    Path temporaryDirectory;

    @Test
    void cachesFullReferenceByNormalizedLanguage() {
        AtomicInteger regionGroupReads = new AtomicInteger();
        EmbroideryOntologyClient ontology = ontologyClient(regionGroupReads);
        CaffeineCacheManager cacheManager = cacheManager();

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(CacheConfig.class);
            context.registerBean(CacheManager.class, () -> cacheManager);
            context.registerBean(EmbroideryOntologyClient.class, () -> ontology);
            context.registerBean(ReferenceService.class);
            context.refresh();

            ReferenceService service = context.getBean(ReferenceService.class);
            Object first = service.getFullReference("bg");
            Object second = service.getFullReference(" BG ");

            assertSame(first, second);
            assertEquals(1, regionGroupReads.get());

            service.getFullReference("en");
            assertEquals(2, regionGroupReads.get());
        }
    }

    @Test
    void evictsFullReferenceAfterSuccessfulOntologyWrite() throws Exception {
        Path ontologyPath = createOntology();
        CaffeineCacheManager cacheManager = cacheManager();

        try (AnnotationConfigApplicationContext context = storeContext(cacheManager, ontologyPath)) {
            Cache cache = requiredCache(cacheManager);
            cache.put("bg", "cached-reference");

            context.getBean(JenaOntologyStore.class).write(model -> null);

            assertNull(cache.get("bg"));
        }
    }

    @Test
    void keepsFullReferenceWhenOntologyWriteRollsBack() throws Exception {
        Path ontologyPath = createOntology();
        CaffeineCacheManager cacheManager = cacheManager();

        try (AnnotationConfigApplicationContext context = storeContext(cacheManager, ontologyPath)) {
            Cache cache = requiredCache(cacheManager);
            cache.put("bg", "cached-reference");

            JenaOntologyStore store = context.getBean(JenaOntologyStore.class);
            assertThrows(IllegalStateException.class, () -> store.write(model -> {
                throw new IllegalStateException("Rejected ontology change");
            }));

            assertNotNull(cache.get("bg"));
        }
    }

    private AnnotationConfigApplicationContext storeContext(
            CacheManager cacheManager,
            Path ontologyPath
    ) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.register(CacheConfig.class);
        context.registerBean(CacheManager.class, () -> cacheManager);
        context.registerBean(
                JenaOntologyStore.class,
                () -> new JenaOntologyStore(ontologyPath, NAMESPACE)
        );
        context.refresh();
        return context;
    }

    private CaffeineCacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(ONTOLOGY_REFERENCE_FULL);
        cacheManager.setCaffeine(Caffeine.newBuilder().maximumSize(4));
        return cacheManager;
    }

    private EmbroideryOntologyClient ontologyClient(AtomicInteger regionGroupReads) {
        return (EmbroideryOntologyClient) Proxy.newProxyInstance(
                EmbroideryOntologyClient.class.getClassLoader(),
                new Class<?>[]{EmbroideryOntologyClient.class},
                (proxy, method, arguments) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return switch (method.getName()) {
                            case "equals" -> proxy == arguments[0];
                            case "hashCode" -> System.identityHashCode(proxy);
                            case "toString" -> "Ontology client test proxy";
                            default -> throw new UnsupportedOperationException(method.getName());
                        };
                    }

                    if (method.getName().equals("listRegionGroups"))
                        regionGroupReads.incrementAndGet();

                    if (method.getReturnType() == List.class)
                        return List.of();

                    if (method.getReturnType() == Optional.class)
                        return Optional.empty();

                    if (method.getReturnType() == boolean.class)
                        return false;

                    throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private Cache requiredCache(CacheManager cacheManager) {
        Cache cache = cacheManager.getCache(ONTOLOGY_REFERENCE_FULL);
        assertNotNull(cache);
        return cache;
    }

    private Path createOntology() throws Exception {
        Path path = temporaryDirectory.resolve("ontology.owl");
        Files.writeString(path, """
                <?xml version="1.0"?>
                <rdf:RDF
                    xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
                    xmlns:owl="http://www.w3.org/2002/07/owl#">
                    <owl:Ontology rdf:about="http://example.com/ontology"/>
                </rdf:RDF>
                """);
        return path;
    }
}
