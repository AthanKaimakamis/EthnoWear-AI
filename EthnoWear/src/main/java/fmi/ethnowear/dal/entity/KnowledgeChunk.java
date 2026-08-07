package fmi.ethnowear.dal.entity;

import fmi.ethnowear.application.enums.KnowledgeChunkType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "KnowledgeChunks", schema = "ethnowear")
public class KnowledgeChunk extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "ChunkType", nullable = false)
    private KnowledgeChunkType chunkType;

    @Column(name = "OntologyIri")
    private String ontologyIri;

    @Column(name = "OntologyLocalName")
    private String ontologyLocalName;

    @Column(name = "Language", nullable = false)
    private String language;

    @Lob
    @Column(name = "Content", nullable = false)
    private String content;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "SourceReferenceId")
    private SourceReference sourceReference;

    @Column(name = "EmbeddingModel")
    private String embeddingModel;

    @Column(name = "EmbeddingId")
    private String embeddingId;
}
