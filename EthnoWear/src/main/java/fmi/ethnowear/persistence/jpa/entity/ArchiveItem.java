package fmi.ethnowear.persistence.jpa.entity;

import fmi.ethnowear.domain.model.archive.ArchiveType;
import fmi.ethnowear.domain.model.archive.TrustedLevel;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "ArchiveItems", schema = "ethnowear")
public class ArchiveItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "SourceReferenceId")
    private SourceReference sourceReference;

    @Column(name = "CollectionId")
    private String collectionId;

    @Column(name = "InventoryNumber")
    private String inventoryNumber;

    @Column(name = "TitleBg")
    private String titleBg;

    @Column(name = "TitleEn")
    private String titleEn;

    @Lob
    @Column(name = "DescriptionBg")
    private String descriptionBg;

    @Lob
    @Column(name = "DescriptionEn")
    private String descriptionEn;

    @Enumerated(EnumType.STRING)
    @Column(name = "ArchiveType", nullable = false)
    private ArchiveType archiveType;

    @Column(name = "PeriodText")
    private String periodText;

    @Column(name = "OriginText")
    private String originText;

    @Column(name = "CurrentLocation")
    private String currentLocation;

    @Enumerated(EnumType.STRING)
    @Column(name = "TrustedLevel", nullable = false)
    private TrustedLevel trustedLevel;

    @Column(name = "OntologyRegionIri")
    private String ontologyRegionIri;

    @Column(name = "OntologyRegionLocalName")
    private String ontologyRegionLocalName;

    @Column(name = "OntologyRegionalEmbroideryIri")
    private String ontologyRegionalEmbroideryIri;

    @Column(name = "OntologyRegionalEmbroideryLocalName")
    private String ontologyRegionalEmbroideryLocalName;
}
