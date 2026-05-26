package fmi.ethnowear.dal.entity;

import fmi.ethnowear.application.enums.FeatureType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "archive_item_features")
public class ArchiveItemFeature extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "archive_item_id", nullable = false)
    private ArchiveItem archiveItem;

    @Enumerated(EnumType.STRING)
    @Column(name = "feature_type", nullable = false)
    private FeatureType featureType;

    @Column(name = "ontology_iri", nullable = false)
    private String ontologyIri;

    @Column(name = "ontology_local_name", nullable = false)
    private String ontologyLocalName;

    private BigDecimal confidence;

    private boolean validated;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_reference_id")
    private SourceReference sourceReference;
}
