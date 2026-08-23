package fmi.ethnowear.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

@ConfigurationProperties(prefix = "ethnowear.worker-api")
public record WorkerApiProperties(
        boolean enabled,
        String token,
        Duration minimumLease,
        Duration defaultLease,
        Duration maximumLease,
        Duration heartbeatInterval,
        Duration recoveryInterval,
        Duration jobTimeout,
        DataSize maximumInputSize,
        DataSize maximumRenditionSize,
        int maximumPageCount,
        int renderDpi,
        int maximumPixelWidth,
        int maximumPixelHeight,
        long maximumPagePixels
) {

    private static final int MINIMUM_TOKEN_BYTES = 32;

    public WorkerApiProperties {
        if (enabled)
            validateToken(token);

        requirePositive(minimumLease, "Minimum lease");
        requirePositive(defaultLease, "Default lease");
        requirePositive(maximumLease, "Maximum lease");
        requirePositive(heartbeatInterval, "Heartbeat interval");
        requirePositive(recoveryInterval, "Recovery interval");
        requirePositive(jobTimeout, "Job timeout");

        if (defaultLease.compareTo(minimumLease) < 0)
            throw new IllegalStateException("Default worker lease cannot be shorter than the minimum lease");

        if (defaultLease.compareTo(maximumLease) > 0)
            throw new IllegalStateException("Default worker lease cannot exceed the maximum lease");

        if (heartbeatInterval.compareTo(maximumLease) >= 0)
            throw new IllegalStateException("Worker heartbeat interval must be shorter than the maximum lease");

        if (maximumInputSize.toBytes() <= 0
                || maximumRenditionSize.toBytes() <= 0
                || maximumPageCount <= 0
                || renderDpi <= 0
                || maximumPixelWidth <= 0
                || maximumPixelHeight <= 0
                || maximumPagePixels <= 0)
            throw new IllegalStateException("Worker resource limits must be positive");
    }

    private static void validateToken(String token) {
        if (token == null
                || token.isBlank()
                || token.getBytes(StandardCharsets.UTF_8).length < MINIMUM_TOKEN_BYTES)
            throw new IllegalStateException("ETHNOWEAR_WORKER_API_TOKEN must contain at least 32 UTF-8 bytes");
    }

    private static void requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative())
            throw new IllegalStateException(name + " must be positive");
    }
}
