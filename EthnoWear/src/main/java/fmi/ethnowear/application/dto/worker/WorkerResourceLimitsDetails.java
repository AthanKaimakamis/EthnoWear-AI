package fmi.ethnowear.application.dto.worker;

public record WorkerResourceLimitsDetails(
        long maximumInputBytes,
        int maximumPageCount,
        int renderDpi,
        int maximumPixelWidth,
        int maximumPixelHeight,
        long maximumRenditionBytes,
        long jobTimeoutSeconds,
        long heartbeatIntervalSeconds,
        long maximumLeaseSeconds
) {
}