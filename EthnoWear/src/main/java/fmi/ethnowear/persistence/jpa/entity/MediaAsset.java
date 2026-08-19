package fmi.ethnowear.persistence.jpa.entity;

import fmi.ethnowear.domain.model.archive.MediaType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "MediaAssets", schema = "ethnowear")
public class MediaAsset extends UpdatableEntity {

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
}
