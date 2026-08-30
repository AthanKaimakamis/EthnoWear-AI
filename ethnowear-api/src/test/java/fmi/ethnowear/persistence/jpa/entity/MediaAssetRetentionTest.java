package fmi.ethnowear.persistence.jpa.entity;

import fmi.ethnowear.domain.model.media.MediaOrigin;
import fmi.ethnowear.domain.model.media.MediaRetentionPolicy;
import fmi.ethnowear.domain.model.media.MediaStorageState;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MediaAssetRetentionTest {

    @Test
    void generatedMediaCanBeScheduledAndMarkedPurged() {
        MediaAsset asset = new MediaAsset();
        LocalDateTime deadline = LocalDateTime.of(2026, 9, 1, 12, 0);
        LocalDateTime purgedAt = deadline.plusDays(1);

        asset.classify(
                MediaOrigin.GENERATED,
                MediaRetentionPolicy.KEEP_ORIGINAL_ONLY
        );
        asset.setFilePath("documents/7/pages/1/render.jpg");
        asset.setChecksum("abc123");
        asset.scheduleRetention(deadline);
        asset.markPurged(purgedAt, "  Retention policy  ");

        assertEquals(MediaStorageState.PURGED, asset.getStorageState());
        assertEquals(deadline, asset.getRetentionUntil());
        assertEquals(purgedAt, asset.getPurgedAt());
        assertEquals("Retention policy", asset.getPurgeReason());
        assertEquals("documents/7/pages/1/render.jpg", asset.getFilePath());
        assertEquals("abc123", asset.getChecksum());
    }

    @Test
    void permanentAndManualMediaCannotBeScheduledOrPurged() {
        MediaAsset permanent = new MediaAsset();
        MediaAsset replacement = new MediaAsset();
        replacement.classify(
                MediaOrigin.MANUAL_REPLACEMENT,
                MediaRetentionPolicy.KEEP_PERMANENTLY
        );

        assertThrows(
                IllegalStateException.class,
                () -> permanent.scheduleRetention(LocalDateTime.now())
        );
        assertThrows(
                IllegalStateException.class,
                () -> replacement.markPurged(
                        LocalDateTime.now(),
                        "Retention policy"
                )
        );
    }
}
