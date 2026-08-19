package fmi.ethnowear.application.service.archive.media;

import fmi.ethnowear.config.MediaStorageProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MediaPathResolverTest {

    @TempDir
    private Path storageRoot;

    @TempDir
    private Path outsideRoot;

    @Test
    void acceptsNormalizedRelativeStorageKeys() {
        MediaPathResolver resolver = resolver();

        assertEquals(
                "documents/document-id/original/source.pdf",
                resolver.normalizeStorageKey("documents/document-id/original/source.pdf")
        );
        assertEquals(
                "thumbnails/asset-id.jpg",
                resolver.normalizeStorageKey("thumbnails/asset-id.jpg")
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/archive/item.jpg",
            "C:/archive/item.jpg",
            "C:\\archive\\item.jpg",
            "\\\\server\\share\\item.jpg"
    })
    void rejectsAbsoluteStorageKeys(String storageKey) {
        assertThrows(
                IllegalArgumentException.class,
                () -> resolver().normalizeStorageKey(storageKey)
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "archive/./item.jpg",
            "archive/../item.jpg",
            "./archive/item.jpg",
            "../archive/item.jpg",
            "archive//item.jpg",
            "archive/item.jpg/",
            "archive\\..\\item.jpg"
    })
    void rejectsTraversalAndNonNormalizedStorageKeys(String storageKey) {
        assertThrows(
                IllegalArgumentException.class,
                () -> resolver().normalizeStorageKey(storageKey)
        );
    }

    @Test
    void rejectsSymlinkedUploadDirectoryOutsideStorageRoot() throws IOException {
        Files.createSymbolicLink(storageRoot.resolve("archive"), outsideRoot);

        assertThrows(
                IllegalArgumentException.class,
                () -> resolver().resolveForWrite("archive/item.jpg")
        );
    }

    private MediaPathResolver resolver() {
        MediaStorageProperties properties = new MediaStorageProperties();
        properties.setStorageRoot(storageRoot);
        return new MediaPathResolver(properties);
    }
}
