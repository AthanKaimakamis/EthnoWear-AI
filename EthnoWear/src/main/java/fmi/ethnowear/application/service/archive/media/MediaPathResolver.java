package fmi.ethnowear.application.service.archive.media;

import fmi.ethnowear.config.MediaStorageProperties;
import fmi.ethnowear.util.ProjectPathResolver;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;

@Component
@RequiredArgsConstructor
public class MediaPathResolver {
    private final MediaStorageProperties properties;

    public Path root() {
        return ProjectPathResolver.resolve(properties.getStorageRoot()).toAbsolutePath().normalize();
    }

    public String normalizeStorageKey(String storageKey) {
        if (storageKey == null || storageKey.isBlank())
            throw new IllegalArgumentException("Media storage key is required");

        if(!storageKey.equals(storageKey.trim()))
            throw new IllegalArgumentException("Media storage key cannot contain surrounding whitespace");

        if(storageKey.contains("\\"))
            throw new IllegalArgumentException("Media storage key must use forward slashes");

        if(storageKey.contains(":"))
            throw new IllegalArgumentException("Absolute media paths are not allowed");

        String normalized = normalize(storageKey);
        if(!normalized.equals(storageKey))
            throw new IllegalArgumentException("Media storage key must already be normalized");

        return normalized;
    }

    public Path resolve(String storageKey) {
        String normalizedKey = normalizeStorageKey(storageKey);
        Path root = root();
        Path resolved = root.resolve(normalizedKey).normalize();

        if(!resolved.startsWith(root))
            throw new IllegalArgumentException("Media path is outside the configured root");

        return resolved;
    }

    public Path resolveExisting(String storageKey) throws IOException {
        Path root = realRoot();
        Path resolved = resolve(storageKey);

        if(!Files.exists(resolved, LinkOption.NOFOLLOW_LINKS))
            return resolved;

        Path realResolve = resolved.toRealPath();
        if(!realResolve.startsWith(root))
            throw new IllegalArgumentException("Media path escapes the configured root");

        return realResolve;
    }


    public Path resolveForWrite(String storageKey) throws IOException {
        Path target = resolve(storageKey);
        Path parent = target.getParent();

        if(parent == null)
            throw new IllegalArgumentException("Media storage key must contain a parent directory");

        Files.createDirectories(parent);

        Path root = realRoot();
        Path realParent = parent.toRealPath();

        if(!realParent.startsWith(root))
            throw new IllegalArgumentException("Media upload directory escapes the configured root");

        if(Files.exists(target, LinkOption.NOFOLLOW_LINKS))
            throw new IllegalArgumentException("Media storage target already exists");

        return target;
    }

    private @NonNull Path realRoot() throws IOException {
        Path root = root();
        Files.createDirectories(root);
        return root.toRealPath();
    }

    private static @NonNull String normalize(@NonNull String storageKey) {
        if(storageKey.startsWith("/") || storageKey.endsWith("/"))
            throw new IllegalArgumentException("Media storage key must be relative");

        String[] segments = storageKey.split("/", -1);
        for (String segment : segments) {
            if(segment.isEmpty())
                throw new IllegalArgumentException("Media storage key contains an empty segment");

            if(segment.equals(".") || segment.equals(".."))
                throw new IllegalArgumentException("Media storage key contains traversal segments");
        }

        final Path relative;
        try {
            relative = Path.of(storageKey);
        } catch (InvalidPathException ex) {
            throw new IllegalArgumentException("Invalid media storage key", ex);
        }

        if (relative.isAbsolute() || relative.getNameCount() == 0)
            throw new IllegalArgumentException("Media storage key must be relative");

        return relative.normalize().toString().replace("\\", "/");
    }
}
