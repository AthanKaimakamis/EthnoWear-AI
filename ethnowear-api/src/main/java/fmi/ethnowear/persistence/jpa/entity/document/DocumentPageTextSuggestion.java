package fmi.ethnowear.persistence.jpa.entity.document;

import fmi.ethnowear.persistence.jpa.entity.AppendOnlyEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "DocumentPageTextSuggestions", schema = "ethnowear")
public class DocumentPageTextSuggestion extends AppendOnlyEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "DocumentPageId", nullable = false, updatable = false)
    private DocumentPage documentPage;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "DocumentPageMediaId", nullable = false, updatable = false)
    private DocumentPageMedia documentPageMedia;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "DocumentPageOcrResultId", nullable = false, updatable = false)
    private DocumentPageOcrResult documentPageOcrResult;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ProcessingJobId", nullable = false, updatable = false)
    private DocumentProcessingJob processingJob;

    @Lob
    @Column(name = "SuggestedText", nullable = false, updatable = false)
    private String suggestedText;

    @Column(name = "SuggestedTextHash", nullable = false, length = 64, updatable = false)
    private String suggestedTextHash;

    @Column(name = "ModelName", nullable = false, length = 100, updatable = false)
    private String modelName;

    @Column(name = "ModelVersion", nullable = false, length = 100, updatable = false)
    private String modelVersion;

    @Column(name = "PromptVersion", nullable = false, length = 100, updatable = false)
    private String promptVersion;

    @Column(name = "RequiresReview", nullable = false, updatable = false)
    private boolean requiresReview;

    @Column(name = "IssuesJson", nullable = false, length = 4000, updatable = false)
    private String issuesJson;

    @Column(name = "UncertainPassagesJson", nullable = false, length = 4000, updatable = false)
    private String uncertainPassagesJson;
}
