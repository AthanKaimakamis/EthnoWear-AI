package fmi.ethnowear.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
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
        long maximumPagePixels,
        int maximumOcrTextCharacters,
        DataSize maximumOcrOutputSize,
        DataSize maximumOcrContextSize,
        DataSize maximumQualityAssessmentPayloadSize,
        int maximumQualitySignals,
        int maximumQualitySignalTypeCharacters,
        int maximumQualitySignalTextCharacters,
        int maximumQualitySummaryCharacters,
        int maximumQualityLimitationsCharacters,
        int maximumQualityMessageCharacters,
        DataSize maximumVisionAssessmentPayloadSize,
        int maximumVisionSuggestionCharacters,
        int maximumVisionIssuesJsonCharacters,
        int maximumVisionIssues,
        int maximumVisionUncertainPassages,
        int maximumVisionIssueCodeCharacters,
        int maximumVisionExcerptCharacters,
        int maximumVisionReasonCharacters,
        int maximumVisionModelNameCharacters,
        int maximumVisionModelVersionCharacters,
        int maximumVisionPromptVersionCharacters
) {

    private static final int MINIMUM_TOKEN_BYTES = 32;

    public WorkerApiProperties(
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
            long maximumPagePixels,
            int maximumOcrTextCharacters,
            DataSize maximumOcrOutputSize
    ) {
        this(
                enabled,
                token,
                minimumLease,
                defaultLease,
                maximumLease,
                heartbeatInterval,
                recoveryInterval,
                jobTimeout,
                maximumInputSize,
                maximumRenditionSize,
                maximumPageCount,
                renderDpi,
                maximumPixelWidth,
                maximumPixelHeight,
                maximumPagePixels,
                maximumOcrTextCharacters,
                maximumOcrOutputSize,
                DataSize.ofMegabytes(16),
                DataSize.ofMegabytes(1),
                100,
                100,
                500,
                1000,
                1000,
                1000,
                DataSize.ofMegabytes(10),
                2000000,
                4000,
                100,
                100,
                100,
                500,
                1000,
                100,
                100,
                100
        );
    }

    @ConstructorBinding
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
                || maximumPagePixels <= 0
                || maximumOcrTextCharacters <= 0
                || maximumOcrOutputSize.toBytes() <= 0
                || maximumOcrContextSize.toBytes() <= 0
                || maximumQualityAssessmentPayloadSize.toBytes() <= 0
                || maximumQualitySignals <= 0
                || maximumQualitySignalTypeCharacters <= 0
                || maximumQualitySignalTextCharacters <= 0
                || maximumQualitySummaryCharacters <= 0
                || maximumQualityLimitationsCharacters <= 0
                || maximumQualityMessageCharacters <= 0
                || maximumVisionAssessmentPayloadSize.toBytes() <= 0
                || maximumVisionSuggestionCharacters <= 0
                || maximumVisionIssuesJsonCharacters <= 0
                || maximumVisionIssues <= 0
                || maximumVisionUncertainPassages <= 0
                || maximumVisionIssueCodeCharacters <= 0
                || maximumVisionExcerptCharacters <= 0
                || maximumVisionReasonCharacters <= 0
                || maximumVisionModelNameCharacters <= 0
                || maximumVisionModelVersionCharacters <= 0
                || maximumVisionPromptVersionCharacters <= 0)
            throw new IllegalStateException("Worker resource limits must be positive");

        if (maximumQualitySignalTypeCharacters > 100
                || maximumQualitySignalTextCharacters > 500
                || maximumQualitySummaryCharacters > 1000
                || maximumQualityLimitationsCharacters > 1000
                || maximumQualityMessageCharacters > 1000
                || maximumVisionIssuesJsonCharacters > 4000
                || maximumVisionIssueCodeCharacters > 100
                || maximumVisionExcerptCharacters > 4000
                || maximumVisionReasonCharacters > 4000
                || maximumVisionModelNameCharacters > 100
                || maximumVisionModelVersionCharacters > 100
                || maximumVisionPromptVersionCharacters > 100)
            throw new IllegalStateException("Worker quality limits exceed database column limits");
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
