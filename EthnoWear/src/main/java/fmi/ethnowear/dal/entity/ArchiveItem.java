package fmi.ethnowear.dal.entity;

import fmi.ethnowear.application.enums.ArchiveType;
import fmi.ethnowear.application.enums.TrustedLevel;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "archive_items")
public class ArchiveItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_reference_id")
    private SourceReference sourceReference;

    @Column(name = "collection_id")
    private String collectionId;

    @Column(name = "inventory_number")
    private String inventoryNumber;

    @Column(name = "title_bg")
    private String titleBg;

    @Column(name = "title_en")
    private String titleEn;

    @Column(name = "description_bg", columnDefinition = "TEXT")
    private String descriptionBg;

    @Column(name = "description_en", columnDefinition = "TEXT")
    private String descriptionEn;

    @Enumerated(EnumType.STRING)
    @Column(name = "archive_type", nullable = false)
    private ArchiveType archiveType;

    @Column(name = "period_text")
    private String periodText;

    @Column(name = "origin_text")
    private String originText;

    @Column(name = "current_location")
    private String currentLocation;

    @Enumerated(EnumType.STRING)
    @Column(name = "trusted_level", nullable = false)
    private TrustedLevel trustedLevel;

    @Column(name = "ontology_region_iri")
    private String ontologyRegionIri;

    @Column(name = "ontology_region_local_name")
    private String ontologyRegionLocalName;

    @Column(name = "ontology_regional_embroidery_iri")
    private String ontologyRegionalEmbroideryIri;

    @Column(name = "ontology_regional_embroidery_local_name")
    private String ontologyRegionalEmbroideryLocalName;
}
