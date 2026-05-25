package fmi.ethnowear.config;

import java.nio.file.Files;
import java.nio.file.Path;

public final class ProjectPathResolver {

    private static final Path MODULE_DIRECTORY = Path.of("EthnoWear");

    private ProjectPathResolver() {
    }

    public static Path resolve(Path path) {
        if (path.isAbsolute() || Files.exists(path)) {
            return path.normalize();
        }

        Path moduleRelativePath = MODULE_DIRECTORY.resolve(path);
        if (Files.exists(moduleRelativePath)) {
            return moduleRelativePath.normalize();
        }

        return path.normalize();
    }
}
