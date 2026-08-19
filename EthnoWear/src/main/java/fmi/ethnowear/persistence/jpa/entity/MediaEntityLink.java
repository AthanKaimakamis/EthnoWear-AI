package fmi.ethnowear.persistence.jpa.entity;

import fmi.ethnowear.domain.model.ontology.FeatureType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "MediaEntityLinks", schema = "ethnowear")
public class MediaEntityLink extends UpdatableEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "MediaAssetId", nullable = false)
    private MediaAsset mediaAsset;

    @Enumerated(EnumType.STRING)
    @Column(name = "EntityType", nullable = false)
    private FeatureType entityType;

    @Column(name = "OntologyIri", nullable = false, length = 1000)
    private String ontologyIri;

    @Column(name = "OntologyLocalName", nullable = false)
    private String ontologyLocalName;

    @Column(name = "Description", length = 1000)
    private String description;
}
