package fmi.ethnowear.util;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectPathResolverTest {

    @Test
    void resolvesModuleResourcesFromTheModuleOrRepositoryRoot() {
        Path resolved = ProjectPathResolver.resolve(
                Path.of("Ontology/EthnoWear.owx")
        );

        assertTrue(Files.isRegularFile(resolved));
    }
}
