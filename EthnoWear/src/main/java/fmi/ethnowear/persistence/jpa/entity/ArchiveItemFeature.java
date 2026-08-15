package fmi.ethnowear.persistence.jpa.entity;

import fmi.ethnowear.domain.model.ontology.FeatureType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "ArchiveItemFeatures", schema = "ethnowear")
public class ArchiveItemFeature extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ArchiveItemId", nullable = false)
    private ArchiveItem archiveItem;

    @Enumerated(EnumType.STRING)
    @Column(name = "FeatureType", nullable = false)
    private FeatureType featureType;

    @Column(name = "OntologyIri", nullable = false)
    private String ontologyIri;

    @Column(name = "OntologyLocalName", nullable = false)
    private String ontologyLocalName;

    @Column(name = "Confidence", precision = 5, scale = 4)
    private BigDecimal confidence;

    @Column(name = "Validated", nullable = false)
    private boolean validated;

    @Lob
    @Column(name = "Notes")
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "SourceReferenceId")
    private SourceReference sourceReference;
}
