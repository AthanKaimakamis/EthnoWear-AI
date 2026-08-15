package fmi.ethnowear.application.service.archive.media;

import fmi.ethnowear.application.exception.ResourceNotFoundException;
import fmi.ethnowear.config.MediaStorageProperties;
import fmi.ethnowear.util.ProjectPathResolver;
import fmi.ethnowear.persistence.jpa.entity.MediaAsset;
import fmi.ethnowear.persistence.jpa.repository.MediaAssetRepository;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Set;

import static fmi.ethnowear.util.TextUtils.isBlank;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MediaDeliveryService {

    private static final Set<String> ALLOWED_REDIRECT_SCHEMES = Set.of(
            "http",
            "https"
    );

    private final MediaAssetRepository assetRepository;
    private final MediaStorageProperties properties;

    public MediaDelivery findById(Long id) {
        MediaAsset asset = assetRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Media asset", id));

        if(!isBlank(asset.getStorageUrl()))
            return redirect(asset.getStorageUrl(), id);

        if(!isBlank(asset.getFilePath()))
            return local(asset, id);

        throw new ResourceNotFoundException("Media content", id);
    }

    @Contract("_, _ -> new")
    private @NonNull MediaDelivery redirect(@NonNull String storageUrl, Long id) {
        URI location;

        try {
            location = URI.create(storageUrl.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("Invalid media storage URL for asset: " + id, ex);
        }

        String scheme = location.getScheme();

        if(scheme == null || !ALLOWED_REDIRECT_SCHEMES.contains(scheme.toLowerCase()))
            throw new IllegalStateException("Unsupported media storage URL for asset: " + id);

        return new MediaDelivery.Redirect(location);
    }

    @Contract("_, _ -> new")
    private @NonNull MediaDelivery local(@NonNull MediaAsset asset, Long id) {
        Path root = ProjectPathResolver
                .resolve(properties.getStorageRoot())
                .toAbsolutePath()
                .normalize();

        Path relativePath;

        try {
            relativePath = Path.of(asset.getFilePath().trim());
        } catch (InvalidPathException ex) {
            throw new IllegalStateException("Invalid media file path for asset: " + id, ex);
        }

        if (relativePath.isAbsolute())
            throw new IllegalStateException("Media file path is outside the storage root: " + id);

        Path file = root.resolve(relativePath).normalize();

        if (!file.startsWith(root))
            throw new IllegalStateException("Media file path is outside the storage root: " + id);

        if(!Files.isRegularFile(file) || !Files.isReadable(file))
            throw new ResourceNotFoundException("Media file", id);

        try {
            return new MediaDelivery.Local(
                    new FileSystemResource(file),
                    asset.getMimeType(),
                    asset.getFileName(),
                    Files.size(file)
            );
        } catch (IOException ex) {
            throw new IllegalStateException("Could not read media file: " + id, ex);
        }

    }
}
