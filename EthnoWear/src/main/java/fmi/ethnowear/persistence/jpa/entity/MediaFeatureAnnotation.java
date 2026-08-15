package fmi.ethnowear.persistence.jpa.entity;

import fmi.ethnowear.domain.model.archive.MediaFeatureAnnotationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "MediaFeatureAnnotations", schema = "ethnowear")
public class MediaFeatureAnnotation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ArchiveItemMediaId", nullable = false)
    private ArchiveItemMedia archiveItemMedia;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ArchiveItemFeatureId", nullable = false)
    private ArchiveItemFeature archiveItemFeature;

    @Enumerated(EnumType.STRING)
    @Column(name = "AnnotationType", nullable = false)
    private MediaFeatureAnnotationType annotationType;

    @Column(name = "X", precision = 9, scale = 6)
    private BigDecimal x;

    @Column(name = "Y", precision = 9, scale = 6)
    private BigDecimal y;

    @Column(name = "Width", precision = 9, scale = 6)
    private BigDecimal width;

    @Column(name = "Height", precision = 9, scale = 6)
    private BigDecimal height;

    @Lob
    @Column(name = "Note")
    private String note;
}
