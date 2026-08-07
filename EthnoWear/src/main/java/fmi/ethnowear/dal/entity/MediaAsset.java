package fmi.ethnowear.dal.entity;

import fmi.ethnowear.application.enums.MediaType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "MediaAssets", schema = "ethnowear")
public class MediaAsset extends BaseEntity {

    @Column(name = "FileName")
    private String fileName;

    @Column(name = "FilePath")
    private String filePath;

    @Column(name = "StorageUrl")
    private String storageUrl;

    @Column(name = "MimeType")
    private String mimeType;

    @Enumerated(EnumType.STRING)
    @Column(name = "MediaType", nullable = false)
    private MediaType mediaType;

    @Column(name = "Width")
    private Integer width;

    @Column(name = "Height")
    private Integer height;

    @Column(name = "SizeBytes")
    private Long sizeBytes;

    @Column(name = "Checksum")
    private String checksum;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "SourceReferenceId")
    private SourceReference sourceReference;
}
