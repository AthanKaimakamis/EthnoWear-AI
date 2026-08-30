package fmi.ethnowear.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Getter
@Setter
@ConfigurationProperties(prefix = "ethnowear.document.chunking")
public class DocumentChunkingProperties {

    private boolean enabled = true;
    private int maximumChunkCharacters = 1600;
    private int overlapCharacters = 150;
    private String strategy = "PAGE_AWARE_PARAGRAPH";
    private String version = "1";
    private Duration executionInterval = Duration.ofSeconds(5);
    private Duration heartbeatInterval = Duration.ofSeconds(30);
    private Duration maximumExecutionTime = Duration.ofMinutes(10);
    private Duration retryDelay = Duration.ofSeconds(30);
}
