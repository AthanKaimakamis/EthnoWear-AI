package fmi.ethnowear.application.service.document.retention;

import fmi.ethnowear.application.exception.DocumentDependencyConflictException;
import fmi.ethnowear.application.service.archive.media.storage.MediaFileHasher;
import fmi.ethnowear.application.service.archive.media.storage.MediaPathResolver;
import fmi.ethnowear.config.MediaStorageProperties;
import fmi.ethnowear.domain.model.document.DocumentPageRenditionType;
import fmi.ethnowear.domain.model.media.MediaOrigin;
import fmi.ethnowear.domain.model.media.MediaRetentionPolicy;
import fmi.ethnowear.domain.model.media.MediaStorageState;
import fmi.ethnowear.persistence.jpa.entity.MediaAsset;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPageMedia;
import fmi.ethnowear.persistence.jpa.repository.MediaAssetRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageMediaRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageFigureRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentProcessingJobRepository;
import fmi.ethnowear.testutil.EntityTestUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static fmi.ethnowear.support.RepositoryTestProxies.proxy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MediaAssetPurgeServiceTest {

    @TempDir
    private Path storageRoot;

    @Test
    void deletesVerifiedGeneratedContentButPreservesMetadata() throws Exception {
        Path file = storageRoot.resolve("documents/7/pages/1/render.jpg");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "generated-page");
        MediaFileHasher hasher = new MediaFileHasher();
        MediaAsset asset = generatedAsset(file, hasher.sha256(file));
        DocumentPageMedia link = generatedLink(asset);
        AtomicBoolean saved = new AtomicBoolean();

        MediaAssetPurgeService service = service(
                asset,
                List.of(link),
                false,
                saved,
                hasher
        );

        assertTrue(service.purge(asset.getId()));
        assertFalse(Files.exists(file));
        assertFalse(link.isPreferredOcrInput());
        assertEquals(MediaStorageState.PURGED, asset.getStorageState());
        assertEquals("documents/7/pages/1/render.jpg", asset.getFilePath());
        assertTrue(saved.get());
    }

    @Test
    void refusesMediaReferencedByAnActiveProcessingJob() throws Exception {
        Path file = storageRoot.resolve("documents/7/pages/1/render.jpg");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "generated-page");
        MediaFileHasher hasher = new MediaFileHasher();
        MediaAsset asset = generatedAsset(file, hasher.sha256(file));

        assertThrows(
                DocumentDependencyConflictException.class,
                () -> service(
                        asset,
                        List.of(generatedLink(asset)),
                        true,
                        new AtomicBoolean(),
                        hasher
                ).purge(asset.getId())
        );
        assertTrue(Files.exists(file));
    }

    @Test
    void refusesManualReplacementMedia() {
        MediaAsset asset = new MediaAsset();
        asset.classify(
                MediaOrigin.MANUAL_REPLACEMENT,
                MediaRetentionPolicy.KEEP_PERMANENTLY
        );
        EntityTestUtils.setId(asset, 31L);

        assertThrows(
                DocumentDependencyConflictException.class,
                () -> service(
                        asset,
                        List.of(),
                        false,
                        new AtomicBoolean(),
                        new MediaFileHasher()
                ).purge(asset.getId())
        );
    }

    private MediaAsset generatedAsset(Path file, String checksum) {
        MediaAsset asset = new MediaAsset();
        asset.classify(
                MediaOrigin.GENERATED,
                MediaRetentionPolicy.KEEP_ORIGINAL_ONLY
        );
        asset.setFilePath(storageRoot.relativize(file).toString());
        asset.setChecksum(checksum);
        asset.scheduleRetention(LocalDateTime.of(2026, 8, 1, 0, 0));
        EntityTestUtils.setId(asset, 31L);
        return asset;
    }

    private DocumentPageMedia generatedLink(MediaAsset asset) {
        DocumentPageMedia link = new DocumentPageMedia();
        link.setMediaAsset(asset);
        link.setRenditionType(DocumentPageRenditionType.PDF_PAGE_RENDER);
        link.setOriginal(false);
        link.setPreferredOcrInput(true);
        return link;
    }

    private MediaAssetPurgeService service(
            MediaAsset asset,
            List<DocumentPageMedia> links,
            boolean activeJob,
            AtomicBoolean saved,
            MediaFileHasher hasher
    ) {
        MediaAssetRepository assets = proxy(
                MediaAssetRepository.class,
                (ignored, method, arguments) -> switch (method.getName()) {
                    case "findByIdForUpdate" -> Optional.of(asset);
                    case "saveAndFlush" -> {
                        saved.set(true);
                        yield arguments[0];
                    }
                    default -> throw new AssertionError(
                            "Unexpected media call: " + method.getName()
                    );
                }
        );
        DocumentPageMediaRepository pageMedia = proxy(
                DocumentPageMediaRepository.class,
                (ignored, method, arguments) -> switch (method.getName()) {
                    case "findByMediaAsset_Id" -> links;
                    case "saveAll" -> links;
                    default -> throw new AssertionError(
                            "Unexpected page-media call: " + method.getName()
                    );
                }
        );
        DocumentProcessingJobRepository jobs = proxy(
                DocumentProcessingJobRepository.class,
                (ignored, method, arguments) -> {
                    if (method.getName().startsWith(
                            "existsByInputMediaAsset_"
                    ))
                        return activeJob;

                    throw new AssertionError(
                            "Unexpected job call: " + method.getName()
                    );
                }
        );
        DocumentPageFigureRepository figures = proxy(
                DocumentPageFigureRepository.class,
                (ignored, method, arguments) -> {
                    if (method.getName().equals("existsByMediaAsset_Id"))
                        return false;

                    throw new AssertionError(
                            "Unexpected figure call: " + method.getName()
                    );
                }
        );
        MediaStorageProperties properties = new MediaStorageProperties();
        properties.setStorageRoot(storageRoot);
        Clock clock = Clock.fixed(
                Instant.parse("2026-08-24T12:00:00Z"),
                ZoneOffset.UTC
        );

        return new MediaAssetPurgeService(
                assets,
                pageMedia,
                jobs,
                figures,
                new GeneratedDocumentMediaPolicy(),
                new MediaPathResolver(properties),
                hasher,
                fmi.ethnowear.support.ManagementEventTestSupport.events(),
                clock
        );
    }
}
