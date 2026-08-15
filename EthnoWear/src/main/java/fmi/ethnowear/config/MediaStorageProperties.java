package fmi.ethnowear.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

@Setter
@Getter
@ConfigurationProperties(prefix = "ethnowear.media")
public class MediaStorageProperties {
    private Path storageRoot = Path.of("media");
}
