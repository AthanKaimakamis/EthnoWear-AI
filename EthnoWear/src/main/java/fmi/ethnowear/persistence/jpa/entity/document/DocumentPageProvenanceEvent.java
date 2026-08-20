package fmi.ethnowear.persistence.jpa.entity.document;

import fmi.ethnowear.domain.model.document.provenance.ProvenanceEventType;
import fmi.ethnowear.domain.model.document.provenance.ProvenanceStatus;
import fmi.ethnowear.domain.model.document.provenance.ProvenanceTrustState;
import fmi.ethnowear.persistence.jpa.entity.AppendOnlyEntity;
import fmi.ethnowear.persistence.jpa.entity.SourceReference;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "DocumentPageProvenanceEvents", schema = "ethnowear")
public class DocumentPageProvenanceEvent extends AppendOnlyEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "DocumentPageId", nullable = false, updatable = false)
    private DocumentPage documentPage;

    @Enumerated(EnumType.STRING)
    @Column(name = "EventType", nullable = false, length = 50, updatable = false)
    private ProvenanceEventType eventType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "PreviousSourceReferenceId", updatable = false)
    private SourceReference previousSourceReference;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "NewSourceReferenceId", updatable = false)
    private SourceReference newSourceReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "PreviousProvenanceStatus", length = 50, updatable = false)
    private ProvenanceStatus previousProvenanceStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "NewProvenanceStatus", nullable = false, length = 50, updatable = false)
    private ProvenanceStatus newProvenanceStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "PreviousTrustState", length = 50, updatable = false)
    private ProvenanceTrustState previousTrustState;

    @Enumerated(EnumType.STRING)
    @Column(name = "NewTrustState", nullable = false, length = 50, updatable = false)
    private ProvenanceTrustState newTrustState;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "PreviousCanonicalDocumentPageId", updatable = false)
    private DocumentPage previousCanonicalDocumentPage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "NewCanonicalDocumentPageId", updatable = false)
    private DocumentPage newCanonicalDocumentPage;

    @Column(name = "ReviewedBy", nullable = false, length = 150, updatable = false)
    private String reviewedBy;

    @Column(name = "Reason", nullable = false, length = 1000, updatable = false)
    private String reason;
}
