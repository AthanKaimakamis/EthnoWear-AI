package fmi.ethnowear.application.service.archive.media;

import fmi.ethnowear.config.MediaStorageProperties;
import fmi.ethnowear.util.ProjectPathResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;

@Component
@RequiredArgsConstructor
public class MediaPathResolver {
    private final MediaStorageProperties properties;

    public Path root() {
        return ProjectPathResolver.resolve(properties.getStorageRoot()).toAbsolutePath().normalize();
    }

    public Path resolve(String storedRelativePath) {
        if (storedRelativePath == null || storedRelativePath.isBlank())
            throw new IllegalArgumentException("Media path is required");

        final Path relative;
        try {
            relative = Path.of(storedRelativePath.trim());
        } catch (InvalidPathException ex) {
            throw new IllegalArgumentException("Invalid media path", ex);
        }

        if (relative.isAbsolute() || relative.getNameCount() == 0)
            throw new IllegalArgumentException("Media path must be relative");

        for (Path part : relative)
            if (part.toString().equals("..") || part.toString().equals("."))
                throw new IllegalArgumentException("Media path contains traversal segments");

        Path root = root();
        Path resolved = root.resolve(relative).normalize();
        if (!resolved.startsWith(root))
            throw new IllegalArgumentException("Media path is outside the configured root");
        return resolved;
    }
}
