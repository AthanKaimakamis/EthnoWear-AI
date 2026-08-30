package fmi.ethnowear.application.service.document.query;

import fmi.ethnowear.application.dto.document.query.workflow.DocumentPageWorkflowStepStatus;
import fmi.ethnowear.application.dto.document.query.workflow.DocumentPageWorkflowStepType;
import fmi.ethnowear.domain.model.document.quality.AssessmentType;
import fmi.ethnowear.domain.model.document.review.ReviewState;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPage;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPageMedia;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPageOcrResult;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPageQualityAssessment;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageMediaRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageOcrResultRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageQualityAssessmentRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentProcessingJobRepository;
import fmi.ethnowear.testutil.EntityTestUtils;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;

import java.util.List;
import java.util.Optional;

import static fmi.ethnowear.support.RepositoryTestProxies.proxy;
import static org.junit.jupiter.api.Assertions.assertEquals;

class DocumentPageWorkflowQueryServiceTest {

    @Test
    void returnsAuthoritativeOrderedWorkflowAndCompletedCount() {
        DocumentPage page = new DocumentPage();
        EntityTestUtils.setId(page, 9L);
        page.setReviewState(ReviewState.REVIEW_REQUIRED);

        DocumentPageRepository pages = proxy(
                DocumentPageRepository.class,
                (ignored, method, arguments) -> Optional.of(page)
        );
        DocumentPageMediaRepository media = proxy(
                DocumentPageMediaRepository.class,
                (ignored, method, arguments) -> Optional.of(new DocumentPageMedia())
        );
        DocumentPageOcrResultRepository ocr = proxy(
                DocumentPageOcrResultRepository.class,
                (ignored, method, arguments) -> Optional.of(new DocumentPageOcrResult())
        );
        DocumentPageQualityAssessmentRepository quality = proxy(
                DocumentPageQualityAssessmentRepository.class,
                (ignored, method, arguments) -> {
                    DocumentPageQualityAssessment assessment =
                            new DocumentPageQualityAssessment();
                    assessment.setAssessmentType(
                            AssessmentType.COMBINED_OCR_QUALITY
                    );
                    return List.of(assessment);
                }
        );
        DocumentProcessingJobRepository jobs = proxy(
                DocumentProcessingJobRepository.class,
                (ignored, method, arguments) -> new PageImpl<>(List.of())
        );

        var result = new DocumentPageWorkflowQueryService(
                pages,
                media,
                ocr,
                quality,
                jobs
        ).progress(7L, 9L);

        assertEquals(3, result.completedSteps());
        assertEquals(4, result.totalSteps());
        assertEquals(
                List.of(
                        DocumentPageWorkflowStepType.IMAGE_EXTRACTION,
                        DocumentPageWorkflowStepType.OCR,
                        DocumentPageWorkflowStepType.OCR_QUALITY_ASSESSMENT,
                        DocumentPageWorkflowStepType.HUMAN_REVIEW
                ),
                result.steps().stream().map(step -> step.step()).toList()
        );
        assertEquals(
                DocumentPageWorkflowStepStatus.QUEUED,
                result.steps().getLast().status()
        );
    }
}
