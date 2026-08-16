package fmi.ethnowear.config;

import fmi.ethnowear.application.service.archive.media.MediaPathResolver;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;

@Component
@RequiredArgsConstructor
public class MediaStorageInitializer {
    private final MediaPathResolver paths;

    @PostConstruct
    void initialize() throws IOException {
        Files.createDirectories(paths.root());
        Files.createDirectories(paths.resolve("archive"));
        Files.createDirectories(paths.resolve("documents"));
        Files.createDirectories(paths.resolve("entities"));
        Files.createDirectories(paths.resolve("thumbnails"));
    }
}
