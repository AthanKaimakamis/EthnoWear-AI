package fmi.ethnowear.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "ethnowear.management-events")
public record ManagementEventProperties(
        Duration connectionTimeout,
        Duration heartbeatInterval,
        int replayCapacity
) {

    public ManagementEventProperties {
        if (connectionTimeout == null
                || connectionTimeout.isZero()
                || connectionTimeout.isNegative())
            throw new IllegalStateException("Management event connection timeout must be positive");

        if (heartbeatInterval == null
                || heartbeatInterval.isZero()
                || heartbeatInterval.isNegative())
            throw new IllegalStateException("Management event heartbeat interval must be positive");

        if (replayCapacity <= 0)
            throw new IllegalStateException("Management event replay capacity must be positive");
    }
}