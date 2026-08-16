package fmi.ethnowear.application.service.archive.media;

import fmi.ethnowear.application.dto.archive.media.MediaUploadRequest;
import fmi.ethnowear.config.MediaStorageProperties;
import fmi.ethnowear.domain.model.archive.MediaType;
import fmi.ethnowear.persistence.jpa.entity.MediaAsset;
import fmi.ethnowear.persistence.jpa.repository.MediaAssetRepository;
import fmi.ethnowear.persistence.jpa.repository.SourceReferenceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicLong;

import static fmi.ethnowear.support.RepositoryTestProxies.proxy;
import static org.junit.jupiter.api.Assertions.*;

class MediaUploadServiceTest {
    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");

    @TempDir Path root;

    @Test
    void storesUniqueRelativePathsAndConsistentMetadata() throws Exception {
        MediaUploadService service = service();
        var request = new MediaUploadRequest(null, MediaType.IMAGE, "entities", "Licensed demo");
        var first = service.upload(file("original.png", PNG, "image/png"), request);
        var second = service.upload(file("original.png", PNG, "image/png"), request);

        assertEquals("original.png", first.fileName());
        assertEquals("image/png", first.mimeType());
        assertEquals((long) PNG.length, first.sizeBytes());
        assertEquals(64, first.checksum().length());
        assertEquals("Licensed demo", first.description());
        assertFalse(Path.of(first.filePath()).isAbsolute());
        assertNotEquals(first.filePath(), second.filePath());
        assertTrue(Files.isRegularFile(root.resolve(first.filePath())));
        assertTrue(Files.isRegularFile(root.resolve(first.thumbnailPath())));
    }

    @Test
    void rejectsOversizedAndSpoofedFilesWithoutLeavingContent() throws Exception {
        MediaStorageProperties properties = properties();
        properties.setMaxFileSize(DataSize.ofBytes(3));
        assertThrows(IllegalArgumentException.class, () -> service(properties).upload(
                file("large.png", PNG, "image/png"),
                new MediaUploadRequest(null, MediaType.IMAGE, "archive", null)));

        MediaUploadService normal = service();
        assertThrows(IllegalArgumentException.class, () -> normal.upload(
                file("fake.png", "not-png".getBytes(), "image/png"),
                new MediaUploadRequest(null, MediaType.IMAGE, "archive", null)));
        try (var files = Files.walk(root)) {
            assertEquals(0, files.filter(Files::isRegularFile).count());
        }
    }

    private MediaUploadService service() { return service(properties()); }

    private MediaUploadService service(MediaStorageProperties properties) {
        AtomicLong ids = new AtomicLong();
        MediaAssetRepository assets = proxy(MediaAssetRepository.class, (ignored, method, args) -> {
            if (method.getName().equals("save")) {
                MediaAsset asset = (MediaAsset) args[0];
                asset.setId(ids.incrementAndGet());
                return asset;
            }
            throw new AssertionError("Unexpected repository call: " + method.getName());
        });
        SourceReferenceRepository references = proxy(SourceReferenceRepository.class,
                (ignored, method, args) -> { throw new AssertionError("Unexpected repository call"); });
        MediaPathResolver resolver = new MediaPathResolver(properties);
        return new MediaUploadService(assets, references, new MediaAssetMapper(), resolver, properties);
    }

    private MediaStorageProperties properties() {
        MediaStorageProperties properties = new MediaStorageProperties();
        properties.setStorageRoot(root);
        return properties;
    }

    private MockMultipartFile file(String name, byte[] bytes, String mime) {
        return new MockMultipartFile("file", name, mime, bytes);
    }
}
