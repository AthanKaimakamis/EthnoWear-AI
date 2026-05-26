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
@Table(name = "knowledge_chunks")
public class KnowledgeChunk extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "chunk_type", nullable = false)
    private KnowledgeChunkType chunkType;

    @Column(name = "ontology_iri")
    private String ontologyIri;

    @Column(name = "ontology_local_name")
    private String ontologyLocalName;

    @Column(nullable = false)
    private String language;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_reference_id")
    private SourceReference sourceReference;

    @Column(name = "embedding_model")
    private String embeddingModel;

    @Column(name = "embedding_id")
    private String embeddingId;
}
