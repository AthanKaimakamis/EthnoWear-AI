package fmi.ethnowear.application.service.archive.media;

import fmi.ethnowear.application.service.archive.media.delivery.MediaDelivery;
import fmi.ethnowear.application.service.archive.media.delivery.MediaDeliveryService;
import fmi.ethnowear.application.service.archive.media.storage.MediaPathResolver;
import fmi.ethnowear.testutil.EntityTestUtils;

import fmi.ethnowear.application.exception.ResourceNotFoundException;
import fmi.ethnowear.config.MediaStorageProperties;
import fmi.ethnowear.persistence.jpa.entity.MediaAsset;
import fmi.ethnowear.persistence.jpa.repository.MediaAssetRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static fmi.ethnowear.support.RepositoryTestProxies.proxy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MediaDeliveryServiceTest {

    @TempDir
    private Path storageRoot;

    @Test
    void returnsRedirectForExternalStorageUrl() {
        MediaAsset asset = asset(7L);
        asset.setStorageUrl("https://cdn.example.org/archive/item.jpg");
        asset.setFilePath("ignored.jpg");

        MediaDelivery.Redirect delivery = assertInstanceOf(
                MediaDelivery.Redirect.class,
                service(Optional.of(asset)).findById(7L)
        );

        assertEquals(asset.getStorageUrl(), delivery.location().toString());
    }

    @Test
    void returnsLocalResourceAndMetadata() throws IOException {
        Path imageDirectory = Files.createDirectories(storageRoot.resolve("images"));
        Path image = imageDirectory.resolve("item.jpg");
        Files.writeString(image, "image-content");

        MediaAsset asset = asset(8L);
        asset.setFilePath("images/item.jpg");
        asset.setFileName("archive-item.jpg");
        asset.setMimeType("image/jpeg");

        MediaDelivery.Local delivery = assertInstanceOf(
                MediaDelivery.Local.class,
                service(Optional.of(asset)).findById(8L)
        );

        assertEquals("archive-item.jpg", delivery.fileName());
        assertEquals("image/jpeg", delivery.mimeType());
        assertEquals(Files.size(image), delivery.contentLength());
        assertEquals(image.toRealPath(), delivery.resource().getFile().toPath());
    }

    @Test
    void throwsWhenMediaAssetDoesNotExist() {
        assertThrows(
                ResourceNotFoundException.class,
                () -> service(Optional.empty()).findById(99L)
        );
    }

    @Test
    void throwsWhenLocalFileDoesNotExist() {
        MediaAsset asset = asset(10L);
        asset.setFilePath("missing.jpg");

        assertThrows(
                ResourceNotFoundException.class,
                () -> service(Optional.of(asset)).findById(10L)
        );
    }

    @Test
    void rejectsPathOutsideConfiguredStorageRoot() {
        MediaAsset asset = asset(11L);
        asset.setFilePath("../outside.jpg");

        assertThrows(
                IllegalStateException.class,
                () -> service(Optional.of(asset)).findById(11L)
        );
    }

    private MediaDeliveryService service(Optional<MediaAsset> asset) {
        MediaAssetRepository repository = proxy(
                MediaAssetRepository.class,
                (ignored, method, arguments) -> asset
        );
        MediaStorageProperties properties = new MediaStorageProperties();
        properties.setStorageRoot(storageRoot);

        return new MediaDeliveryService(repository, new MediaPathResolver(properties));
    }

    private MediaAsset asset(Long id) {
        MediaAsset asset = new MediaAsset();
        EntityTestUtils.setId(asset, id);
        return asset;
    }
}
