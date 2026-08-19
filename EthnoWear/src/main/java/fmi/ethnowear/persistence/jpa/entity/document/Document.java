package fmi.ethnowear.persistence.jpa.entity.document;

import fmi.ethnowear.domain.model.document.*;
import fmi.ethnowear.domain.model.document.indexing.IndexingState;
import fmi.ethnowear.domain.model.document.processing.ProcessingState;
import fmi.ethnowear.domain.model.document.review.ProvenanceStatus;
import fmi.ethnowear.domain.model.document.review.ProvenanceTrustState;
import fmi.ethnowear.domain.model.document.review.ReviewState;
import fmi.ethnowear.persistence.jpa.entity.UpdatableEntity;
import fmi.ethnowear.persistence.jpa.entity.MediaAsset;
import fmi.ethnowear.persistence.jpa.entity.Source;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "Documents", schema = "ethnowear")
public class Document extends UpdatableEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "SourceId")
    private Source source;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "OriginalMediaAssetId")
    private MediaAsset originalMediaAsset;

    @Enumerated(EnumType.STRING)
    @Column(name = "DocumentType", nullable = false, length = 50)
    private DocumentType documentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "ProvenanceStatus", nullable = false, length = 50)
    private ProvenanceStatus provenanceStatus;

    @Column(name = "Title", nullable = false, length = 300)
    private String title;

    @Column(name = "Author", length = 200)
    private String author;

    @Column(name = "Publisher", length = 200)
    private String publisher;

    @Column(name = "PublicationYear")
    private Integer publicationYear;

    @Column(name = "Language", length = 10)
    private String language;

    @Column(name = "PageCount")
    private Integer pageCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "ProcessingState", nullable = false, length = 50)
    private ProcessingState processingState = ProcessingState.UPLOADED;

    @Enumerated(EnumType.STRING)
    @Column(name = "ReviewState", nullable = false, length = 50)
    private ReviewState reviewState = ReviewState.NOT_READY;

    @Enumerated(EnumType.STRING)
    @Column(name = "ProvenanceTrustState", nullable = false, length = 50)
    private ProvenanceTrustState provenanceTrustState = ProvenanceTrustState.UNKNOWN;

    @Enumerated(EnumType.STRING)
    @Column(name = "IndexingState", nullable = false, length = 50)
    private IndexingState indexingState = IndexingState.NOT_ELIGIBLE;

    @Lob
    @Column(name = "Notes")
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "MergedIntoDocumentId")
    private Document mergedIntoDocument;
}
