package fmi.ethnowear.persistence.jpa.entity.document;

import fmi.ethnowear.domain.model.document.DocumentPageRenditionType;
import fmi.ethnowear.persistence.jpa.entity.UpdatableEntity;
import fmi.ethnowear.persistence.jpa.entity.MediaAsset;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "DocumentPageMedia", schema = "ethnowear",
        uniqueConstraints = @UniqueConstraint(
                name = "UQ_DocumentPageMedia_Page_Media",
                columnNames = {"DocumentPageId", "MediaAssetId"}
        )
)
public class DocumentPageMedia extends UpdatableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "DocumentPageId", nullable = false)
    private DocumentPage documentPage;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "MediaAssetId", nullable = false)
    private MediaAsset mediaAsset;

    @Enumerated(EnumType.STRING)
    @Column(name = "RenditionType", nullable = false, length = 50)
    private DocumentPageRenditionType renditionType;

    @Column(name = "IsOriginal", nullable = false)
    private boolean original;

    @Column(name = "IsPreferredOcrInput", nullable = false)
    private boolean preferredOcrInput;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "DerivativeOfDocumentPageMediaId")
    private DocumentPageMedia derivativeOf;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ProducingJobId")
    private DocumentProcessingJob producingJob;

    @Column(name = "DisplayOrder", nullable = false)
    private Integer displayOrder = 0;

    @Column(name = "Width")
    private Integer width;

    @Column(name = "Height")
    private Integer height;

    @Column(name = "Dpi")
    private Integer dpi;

    @Column(name = "ColorMode", length = 50)
    private String colorMode;

    @Column(name = "RenditionHash", length = 128)
    private String renditionHash;

    @Lob
    @Column(name = "Notes")
    private String notes;
}
