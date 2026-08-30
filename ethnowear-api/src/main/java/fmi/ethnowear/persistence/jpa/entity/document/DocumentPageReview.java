package fmi.ethnowear.persistence.jpa.entity.document;

import fmi.ethnowear.domain.model.document.review.ReviewAction;
import fmi.ethnowear.domain.model.document.review.ReviewState;
import fmi.ethnowear.domain.model.document.review.TranscriptionApprovalState;
import fmi.ethnowear.persistence.jpa.entity.AppendOnlyEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "DocumentPageReviews", schema = "ethnowear")
public class DocumentPageReview extends AppendOnlyEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "DocumentPageId", nullable = false, updatable = false)
    private DocumentPage documentPage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "SourceTextSuggestionId", updatable = false)
    private DocumentPageTextSuggestion sourceTextSuggestion;

    @Column(name = "SourceTextSuggestionIssueOrdinal", updatable = false)
    private Integer sourceTextSuggestionIssueOrdinal;

    @Enumerated(EnumType.STRING)
    @Column(name = "ReviewAction", nullable = false, length = 50, updatable = false)
    private ReviewAction reviewAction;

    @Column(name = "Reviewer", nullable = false, length = 150, updatable = false)
    private String reviewer;

    @Lob
    @Column(name = "CorrectedTextSnapshot", updatable = false)
    private String correctedTextSnapshot;

    @Column(name = "CorrectedTextHash", length = 64, updatable = false)
    private String correctedTextHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "PreviousReviewState", length = 50, updatable = false)
    private ReviewState previousReviewState;

    @Enumerated(EnumType.STRING)
    @Column(name = "NewReviewState", nullable = false, length = 50, updatable = false)
    private ReviewState newReviewState;

    @Enumerated(EnumType.STRING)
    @Column(name = "PreviousApprovalState", length = 50, updatable = false)
    private TranscriptionApprovalState previousApprovalState;

    @Enumerated(EnumType.STRING)
    @Column(name = "NewApprovalState", nullable = false, length = 50, updatable = false)
    private TranscriptionApprovalState newApprovalState;

    @Column(name = "Reason", length = 1000, updatable = false)
    private String reason;
}
