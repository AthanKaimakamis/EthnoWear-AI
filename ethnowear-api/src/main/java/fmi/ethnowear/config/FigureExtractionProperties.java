package fmi.ethnowear.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

@ConfigurationProperties(prefix = "ethnowear.worker-api.figure-extraction")
public record FigureExtractionProperties(
        int maximumCandidates,
        int maximumCaptionCharacters,
        int maximumPrintedNumberCharacters,
        DataSize maximumCropSize
) {
    public FigureExtractionProperties {
        if (maximumCandidates <= 0 || maximumCandidates > 500)
            throw new IllegalStateException("Maximum figure candidates must be between 1 and 500");

        if (maximumCaptionCharacters <= 0 || maximumCaptionCharacters > 2000)
            throw new IllegalStateException("Maximum figure caption length must be between 1 and 2000");

        if (maximumPrintedNumberCharacters <= 0 || maximumPrintedNumberCharacters > 100)
            throw new IllegalStateException("Maximum printed figure number length must be between 1 and 100");

        if (maximumCropSize == null || maximumCropSize.toBytes() <= 0)
            throw new IllegalStateException("Maximum figure crop size must be positive");
    }
}
