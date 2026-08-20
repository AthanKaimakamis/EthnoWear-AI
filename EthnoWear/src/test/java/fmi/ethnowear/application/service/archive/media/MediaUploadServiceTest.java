package fmi.ethnowear.application.service.archive.media;

import fmi.ethnowear.application.service.archive.media.asset.MediaAssetMapper;
import fmi.ethnowear.application.service.archive.media.storage.MediaContentValidator;
import fmi.ethnowear.application.service.archive.media.storage.MediaFileCompensation;
import fmi.ethnowear.application.service.archive.media.storage.MediaPathResolver;
import fmi.ethnowear.application.service.archive.media.storage.MediaUploadService;
import fmi.ethnowear.testutil.EntityTestUtils;

import fmi.ethnowear.application.dto.archive.media.MediaUploadRequest;
import fmi.ethnowear.config.MediaStorageProperties;
import fmi.ethnowear.domain.model.archive.MediaType;
import fmi.ethnowear.persistence.jpa.entity.MediaAsset;
import fmi.ethnowear.persistence.jpa.repository.MediaAssetRepository;
import fmi.ethnowear.persistence.jpa.repository.SourceReferenceRepository;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.unit.DataSize;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
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

    @Test
    void acceptsPdfUploadAndStoresItUnderDocuments() {
        var result = service().upload(
                file("source.pdf", pdf(), "application/pdf"),
                new MediaUploadRequest(null, MediaType.PDF, null, "Source document")
        );

        assertEquals("application/pdf", result.mimeType());
        assertEquals(MediaType.PDF, result.mediaType());
        assertTrue(result.filePath().startsWith("documents/"));
        assertTrue(result.filePath().endsWith(".pdf"));
        assertNull(result.thumbnailPath());
        assertTrue(Files.isRegularFile(root.resolve(result.filePath())));
    }

    @Test
    void removesOnlyNewFilesWhenDatabasePersistenceFails() throws Exception {
        Path preExisting = root.resolve("archive/pre-existing.txt");
        Files.createDirectories(preExisting.getParent());
        Files.writeString(preExisting, "keep");

        MediaAssetRepository failingRepository = proxy(
                MediaAssetRepository.class,
                (ignored, method, args) -> {
                    if (method.getName().equals("save"))
                        throw new IllegalStateException("Database unavailable");
                    throw new AssertionError("Unexpected repository call: " + method.getName());
                }
        );

        assertThrows(
                IllegalStateException.class,
                () -> service(properties(), failingRepository).upload(
                        file("capture.png", PNG, "image/png"),
                        new MediaUploadRequest(null, MediaType.IMAGE, "archive", null)
                )
        );

        assertTrue(Files.isRegularFile(preExisting));
        try (var files = Files.walk(root)) {
            assertEquals(1, files.filter(Files::isRegularFile).count());
        }
    }

    @Test
    void removesUploadedFilesWhenOuterTransactionRollsBack() {
        TransactionSynchronizationManager.initSynchronization();

        try {
            var result = service().upload(
                    file("capture.png", PNG, "image/png"),
                    new MediaUploadRequest(null, MediaType.IMAGE, "archive", null)
            );

            Path original = root.resolve(result.filePath());
            Path thumbnail = root.resolve(result.thumbnailPath());
            assertTrue(Files.isRegularFile(original));
            assertTrue(Files.isRegularFile(thumbnail));

            List<TransactionSynchronization> synchronizations =
                    TransactionSynchronizationManager.getSynchronizations();

            assertEquals(1, synchronizations.size());
            synchronizations.forEach(synchronization ->
                    synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK)
            );

            assertFalse(Files.exists(original));
            assertFalse(Files.exists(thumbnail));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void filesystemFailurePreventsMetadataPersistence() throws Exception {
        Path invalidRoot = root.resolve("not-a-directory");
        Files.writeString(invalidRoot, "file");

        MediaStorageProperties properties = properties();
        properties.setStorageRoot(invalidRoot);
        AtomicBoolean persisted = new AtomicBoolean();
        MediaAssetRepository repository = proxy(
                MediaAssetRepository.class,
                (ignored, method, args) -> {
                    if (method.getName().equals("save"))
                        persisted.set(true);
                    throw new AssertionError("Metadata must not be persisted after a filesystem failure");
                }
        );

        assertThrows(
                IllegalStateException.class,
                () -> service(properties, repository).upload(
                        file("capture.png", PNG, "image/png"),
                        new MediaUploadRequest(null, MediaType.IMAGE, "archive", null)
                )
        );

        assertFalse(persisted.get());
    }

    private MediaUploadService service() { return service(properties()); }

    private MediaUploadService service(MediaStorageProperties properties) {
        AtomicLong ids = new AtomicLong();
        MediaAssetRepository assets = proxy(MediaAssetRepository.class, (ignored, method, args) -> {
            if (method.getName().equals("save")) {
                MediaAsset asset = (MediaAsset) args[0];
                EntityTestUtils.setId(asset, ids.incrementAndGet());
                return asset;
            }
            throw new AssertionError("Unexpected repository call: " + method.getName());
        });
        return service(properties, assets);
    }

    private MediaUploadService service(
            MediaStorageProperties properties,
            MediaAssetRepository assets
    ) {
        SourceReferenceRepository references = proxy(SourceReferenceRepository.class,
                (ignored, method, args) -> { throw new AssertionError("Unexpected repository call"); });
        MediaPathResolver resolver = new MediaPathResolver(properties);
        return new MediaUploadService(
                assets,
                references,
                new MediaAssetMapper(),
                resolver,
                properties,
                new MediaContentValidator(),
                new MediaFileCompensation()
        );
    }

    private MediaStorageProperties properties() {
        MediaStorageProperties properties = new MediaStorageProperties();
        properties.setStorageRoot(root);
        return properties;
    }

    private MockMultipartFile file(String name, byte[] bytes, String mime) {
        return new MockMultipartFile("file", name, mime, bytes);
    }

    private byte[] pdf() {
        try (
                PDDocument document = new PDDocument();
                ByteArrayOutputStream output = new ByteArrayOutputStream()
        ) {
            document.addPage(new PDPage());
            document.save(output);
            return output.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Could not create PDF test fixture", ex);
        }
    }
}
