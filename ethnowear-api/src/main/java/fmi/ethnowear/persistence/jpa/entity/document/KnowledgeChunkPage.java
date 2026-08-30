package fmi.ethnowear.persistence.jpa.entity.document;

import fmi.ethnowear.persistence.jpa.entity.UpdatableEntity;
import fmi.ethnowear.persistence.jpa.entity.KnowledgeChunk;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "KnowledgeChunkPages", schema = "ethnowear",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "UQ_KnowledgeChunkPages_Chunk_Page",
                        columnNames = {"KnowledgeChunkId", "DocumentPageId"}
                ),
                @UniqueConstraint(
                        name = "UQ_KnowledgeChunkPages_Chunk_PageOrder",
                        columnNames = {"KnowledgeChunkId", "PageOrder"}
                )
        }
)
public class KnowledgeChunkPage extends UpdatableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "KnowledgeChunkId", nullable = false)
    private KnowledgeChunk knowledgeChunk;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "DocumentPageId", nullable = false)
    private DocumentPage documentPage;

    @Column(name = "PageOrder", nullable = false)
    private Integer pageOrder;

    @Column(name = "StartCharOffset")
    private Integer startCharOffset;

    @Column(name = "EndCharOffset")
    private Integer endCharOffset;

    @Column(name = "StartsOnPage", nullable = false)
    private boolean startsOnPage;

    @Column(name = "EndsOnPage", nullable = false)
    private boolean endsOnPage;

    @Column(name = "CitationPrintedPageNumber", length = 50)
    private String citationPrintedPageNumber;

    @Column(name = "CitationPdfPageIndex")
    private Integer citationPdfPageIndex;

    @Column(name = "CitationLabel", length = 300)
    private String citationLabel;
}
