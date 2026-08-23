package fmi.ethnowear.config;

import org.junit.jupiter.api.Test;

import static fmi.ethnowear.support.WorkerTestFixtures.properties;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorkerApiPropertiesTest {

    @Test
    void requiresHeartbeatIntervalShorterThanMaximumLease() {
        WorkerApiProperties defaults = properties();

        assertThrows(
                IllegalStateException.class,
                () -> copy(defaults, defaults.maximumLease(), defaults.maximumPagePixels())
        );
    }

    @Test
    void requiresPositiveMaximumPagePixels() {
        WorkerApiProperties defaults = properties();

        assertThrows(
                IllegalStateException.class,
                () -> copy(defaults, defaults.heartbeatInterval(), 0)
        );
    }

    private WorkerApiProperties copy(
            WorkerApiProperties defaults,
            java.time.Duration heartbeatInterval,
            long maximumPagePixels
    ) {
        return new WorkerApiProperties(
                defaults.enabled(),
                defaults.token(),
                defaults.minimumLease(),
                defaults.defaultLease(),
                defaults.maximumLease(),
                heartbeatInterval,
                defaults.recoveryInterval(),
                defaults.jobTimeout(),
                defaults.maximumInputSize(),
                defaults.maximumRenditionSize(),
                defaults.maximumPageCount(),
                defaults.renderDpi(),
                defaults.maximumPixelWidth(),
                defaults.maximumPixelHeight(),
                maximumPagePixels
        );
    }
}
