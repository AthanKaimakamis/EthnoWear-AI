package fmi.ethnowear.persistence.jpa.entity;

import fmi.ethnowear.domain.model.archive.MediaType;
import fmi.ethnowear.domain.model.media.MediaOrigin;
import fmi.ethnowear.domain.model.media.MediaRetentionPolicy;
import fmi.ethnowear.domain.model.media.MediaStorageState;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "MediaAssets", schema = "ethnowear")
public class MediaAsset extends UpdatableEntity {

    @Enumerated(EnumType.STRING)
    @Setter(AccessLevel.NONE)
    @Column(name = "Origin", nullable = false, length = 50, updatable = false)
    private MediaOrigin origin = MediaOrigin.USER_UPLOAD;

    @Enumerated(EnumType.STRING)
    @Setter(AccessLevel.NONE)
    @Column(name = "RetentionPolicy", nullable = false, length = 50)
    private MediaRetentionPolicy retentionPolicy =
            MediaRetentionPolicy.KEEP_PERMANENTLY;

    @Enumerated(EnumType.STRING)
    @Setter(AccessLevel.NONE)
    @Column(name = "StorageState", nullable = false, length = 50)
    private MediaStorageState storageState = MediaStorageState.AVAILABLE;

    @Setter(AccessLevel.NONE)
    @Column(name = "RetentionUntil")
    private java.time.LocalDateTime retentionUntil;

    @Setter(AccessLevel.NONE)
    @Column(name = "PurgedAt")
    private java.time.LocalDateTime purgedAt;

    @Setter(AccessLevel.NONE)
    @Column(name = "PurgeReason", length = 500)
    private String purgeReason;

    @Column(name = "FileName", updatable = false)
    private String fileName;

    @Column(name = "FilePath", updatable = false)
    private String filePath;

    @Column(name = "StorageUrl", updatable = false)
    private String storageUrl;

    @Column(name = "MimeType", updatable = false)
    private String mimeType;

    @Enumerated(EnumType.STRING)
    @Column(name = "MediaType", nullable = false, updatable = false)
    private MediaType mediaType;

    @Column(name = "Width", updatable = false)
    private Integer width;

    @Column(name = "Height", updatable = false)
    private Integer height;

    @Column(name = "SizeBytes", updatable = false)
    private Long sizeBytes;

    @Column(name = "Checksum", updatable = false)
    private String checksum;

    @Column(name = "ThumbnailPath", updatable = false)
    private String thumbnailPath;

    @Column(name = "Description", length = 2000)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "SourceReferenceId")
    private SourceReference sourceReference;

    public void classify(
            MediaOrigin origin,
            MediaRetentionPolicy retentionPolicy
    ) {
        if (getId() != null)
            throw new IllegalStateException("Media lifecycle classification is immutable");

        this.origin = java.util.Objects.requireNonNull(
                origin,
                "Media origin is required"
        );
        this.retentionPolicy = java.util.Objects.requireNonNull(
                retentionPolicy,
                "Media retention policy is required"
        );
    }

    public void scheduleRetention(java.time.LocalDateTime retentionUntil) {
        if (retentionPolicy != MediaRetentionPolicy.KEEP_ORIGINAL_ONLY)
            throw new IllegalStateException("Permanent media cannot be scheduled for cleanup");

        if (storageState == MediaStorageState.PURGED)
            return;

        this.retentionUntil = java.util.Objects.requireNonNull(
                retentionUntil,
                "Retention deadline is required"
        );
    }

    public void markPurged(
            java.time.LocalDateTime purgedAt,
            String purgeReason
    ) {
        if (storageState == MediaStorageState.PURGED)
            return;

        if (origin != MediaOrigin.GENERATED
                || retentionPolicy != MediaRetentionPolicy.KEEP_ORIGINAL_ONLY)
            throw new IllegalStateException("Only generated media can be purged");

        if (purgeReason == null || purgeReason.isBlank())
            throw new IllegalArgumentException("Media purge reason is required");

        String normalizedReason = purgeReason.trim();
        if (normalizedReason.length() > 500)
            throw new IllegalArgumentException(
                    "Media purge reason cannot exceed 500 characters"
            );

        this.storageState = MediaStorageState.PURGED;
        this.purgedAt = java.util.Objects.requireNonNull(
                purgedAt,
                "Media purge time is required"
        );
        this.purgeReason = normalizedReason;
    }
}
