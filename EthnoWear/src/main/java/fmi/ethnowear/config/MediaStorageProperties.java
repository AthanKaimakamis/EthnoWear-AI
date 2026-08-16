package fmi.ethnowear.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.util.unit.DataSize;

@Setter
@Getter
@ConfigurationProperties(prefix = "ethnowear.media")
public class MediaStorageProperties {
    private Path storageRoot = Path.of("media");
    private DataSize maxFileSize = DataSize.ofMegabytes(25);
    private Set<String> allowedContentTypes = new LinkedHashSet<>(Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp", "application/pdf"
    ));
}
