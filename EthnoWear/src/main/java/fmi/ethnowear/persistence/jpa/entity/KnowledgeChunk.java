package fmi.ethnowear.persistence.jpa.entity;

import fmi.ethnowear.domain.model.archive.KnowledgeChunkType;
import fmi.ethnowear.domain.model.document.indexing.IndexingState;
import fmi.ethnowear.domain.model.document.indexing.SourceTextType;
import fmi.ethnowear.domain.model.document.review.ProvenanceTrustState;
import fmi.ethnowear.domain.model.document.review.ReviewState;
import fmi.ethnowear.domain.model.document.review.TranscriptionApprovalState;
import fmi.ethnowear.persistence.jpa.entity.document.Document;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "KnowledgeChunks", schema = "ethnowear")
public class KnowledgeChunk extends UpdatableEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "SourceReferenceId")
    private SourceReference sourceReference;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "DocumentId")
    private Document document;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ArchiveItemId")
    private ArchiveItem archiveItem;

    @Enumerated(EnumType.STRING)
    @Column(name = "ChunkType", nullable = false, length = 50)
    private KnowledgeChunkType chunkType;

    @Column(name = "OntologyIri", length = 1000)
    private String ontologyIri;

    @Column(name = "OntologyLocalName", length = 200)
    private String ontologyLocalName;

    @Column(name = "Language", nullable = false, length = 10)
    private String language;

    @Lob
    @Column(name = "Content", nullable = false)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "SourceTextType", nullable = false, length = 50)
    private SourceTextType sourceTextType = SourceTextType.MANUAL_EXCERPT;

    @Column(name = "ChunkOrdinal")
    private Integer chunkOrdinal;

    @Column(name = "ContentHash", nullable = false, length = 128)
    private String contentHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "ReviewState", nullable = false, length = 50)
    private ReviewState reviewState = ReviewState.REVIEW_REQUIRED;

    @Enumerated(EnumType.STRING)
    @Column(name = "TranscriptionApprovalState", nullable = false, length = 50)
    private TranscriptionApprovalState transcriptionApprovalState =
            TranscriptionApprovalState.NOT_REQUIRED;

    @Enumerated(EnumType.STRING)
    @Column(name = "ProvenanceTrustState", nullable = false, length = 50)
    private ProvenanceTrustState provenanceTrustState =
            ProvenanceTrustState.UNKNOWN;

    @Enumerated(EnumType.STRING)
    @Column(name = "IndexingState", nullable = false, length = 50)
    private IndexingState indexingState = IndexingState.NOT_ELIGIBLE;

    @Column(name = "EmbeddingModel", length = 100)
    private String embeddingModel;

    @Column(name = "EmbeddingDimensions")
    private Integer embeddingDimensions;

    @Column(name = "VectorCollection", length = 150)
    private String vectorCollection;

    @Column(name = "VectorPointId", length = 255)
    private String vectorPointId;

    @Column(name = "IndexedContentHash", length = 128)
    private String indexedContentHash;

    @Column(name = "IndexedAt")
    private LocalDateTime indexedAt;

    @Column(name = "IndexingError", length = 1000)
    private String indexingError;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "SupersededByKnowledgeChunkId")
    private KnowledgeChunk supersededBy;
}
