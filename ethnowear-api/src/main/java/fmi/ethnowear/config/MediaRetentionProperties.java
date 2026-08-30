package fmi.ethnowear.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Getter
@Setter
@ConfigurationProperties(prefix = "ethnowear.media.retention")
public class MediaRetentionProperties {
    private boolean enabled = true;
    private Duration defaultPeriod = Duration.ofDays(30);
    private Duration executionInterval = Duration.ofMinutes(1);
    private Duration retryDelay = Duration.ofMinutes(15);
    private Duration maximumExecutionTime = Duration.ofMinutes(30);
}
