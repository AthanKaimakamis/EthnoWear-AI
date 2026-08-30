package fmi.ethnowear.application.service.worker.job;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import fmi.ethnowear.application.dto.worker.quality.WorkerQualityAssessmentCommand;
import fmi.ethnowear.application.dto.worker.quality.WorkerQualitySignalCommand;
import fmi.ethnowear.application.dto.worker.vision.*;
import fmi.ethnowear.application.exception.*;
import fmi.ethnowear.application.model.worker.WorkerClaimCredentials;
import fmi.ethnowear.application.service.document.processing.DocumentProcessingJobKeyFactory;
import fmi.ethnowear.application.service.worker.security.WorkerClaimedJobLoader;
import fmi.ethnowear.config.WorkerApiProperties;
import fmi.ethnowear.domain.model.archive.MediaType;
import fmi.ethnowear.domain.model.document.processing.JobStatus;
import fmi.ethnowear.domain.model.document.processing.JobType;
import fmi.ethnowear.domain.model.document.quality.AssessmentType;
import fmi.ethnowear.domain.model.document.quality.AssessorType;
import fmi.ethnowear.persistence.jpa.entity.MediaAsset;
import fmi.ethnowear.persistence.jpa.entity.document.*;
import fmi.ethnowear.persistence.jpa.repository.document.*;
import fmi.ethnowear.util.ContentHashUtils;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class WorkerVisionOcrAssessmentService {

    private static final int MAXIMUM_STORED_JSON_CHARACTERS = 4000;
    private static final AssessmentType ASSESSMENT_TYPE =
            AssessmentType.VISION_TEXT_COMPARISON;

    private final WorkerClaimedJobLoader jobLoader;
    private final DocumentPageOcrResultRepository ocrResultRepository;
    private final DocumentPageQualityAssessmentRepository assessmentRepository;
    private final DocumentPageQualitySignalRepository signalRepository;
    private final DocumentPageTextSuggestionRepository suggestionRepository;
    private final DocumentProcessingJobKeyFactory jobKeyFactory;
    private final WorkerApiProperties properties;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public WorkerVisionAssessmentContextDetails context(
            Long jobId,
            WorkerClaimCredentials credentials
    ) {
        Context context = requireContext(jobId, credentials);
        List<DocumentPageQualitySignal> signals = signalRepository
                .findByAssessment_IdOrderBySignalOrdinalAscIdAsc(
                        context.deterministicAssessment().getId()
                );

        WorkerVisionAssessmentContextDetails details = toContextDetails(
                context,
                signals
        );

        if (serializedSize(details) > properties.maximumOcrContextSize().toBytes())
            throw new WorkerPayloadTooLargeException(
                    "Vision assessment context exceeds the maximum size"
            );

        return details;
    }

    @Transactional
    public WorkerVisionAssessmentDetails accept(
            Long jobId,
            WorkerClaimCredentials credentials,
            WorkerVisionAssessmentCommand command
    ) {
        Context context = requireContext(jobId, credentials);
        WorkerVisionAssessmentCommand validated = validateSafely(
                command,
                context.ocrResult().getRawText()
        );

        return suggestionRepository.findByProcessingJob_Id(jobId)
                .map(existing -> existing(existing, context, validated))
                .orElseGet(() -> create(context, validated));
    }

    private WorkerVisionAssessmentCommand validateSafely(
            WorkerVisionAssessmentCommand command,
            String assessedOcrText
    ) {
        try {
            return validate(command, assessedOcrText);
        } catch (WorkerVisionValidationException ex) {
            throw ex;
        } catch (IllegalArgumentException ex) {
            throw visionInvalid(
                    "VISION_RESULT_INVALID",
                    "The vision result does not satisfy the worker contract"
            );
        }
    }

    private Context requireContext(
            Long jobId,
            WorkerClaimCredentials credentials
    ) {
        DocumentProcessingJob job = jobLoader.requireActive(jobId, credentials);

        if (job.getStatus() == JobStatus.CANCEL_REQUESTED)
            throw new WorkerClaimConflictException();

        if (job.getJobType() != JobType.VISION_OCR_ASSESSMENT)
            throw new IllegalArgumentException(
                    "Job is not a vision OCR-assessment job"
            );

        DocumentPage page = job.getDocumentPage();

        if (page == null || page.getDocument() == null)
            throw new UnprocessableDocumentEvidenceException(
                    "Vision-assessment job has no valid page"
            );

        if (job.getDocument() != null
                && !Objects.equals(
                        job.getDocument().getId(),
                        page.getDocument().getId()
                ))
            throw new UnprocessableDocumentEvidenceException(
                    "Vision-assessment job document and page are inconsistent"
            );

        MediaAsset input = job.getInputMediaAsset();

        if (input == null)
            throw new ResourceNotFoundException("Job input media", job.getId());

        Long ocrResultId = jobKeyFactory.visionOcrResultId(job.getJobKey());
        Long deterministicAssessmentId = jobKeyFactory
                .visionDeterministicAssessmentId(job.getJobKey());
        DocumentPageOcrResult ocrResult = ocrResultRepository
                .findById(ocrResultId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "OCR result",
                        ocrResultId
                ));

        if (!ocrResult.isCurrent())
            throw new WorkerManifestConflictException(
                    "Vision-assessment OCR result is no longer current"
            );

        DocumentPageMedia pageMedia = ocrResult.getDocumentPageMedia();

        if (ocrResult.getDocumentPage() == null
                || !Objects.equals(ocrResult.getDocumentPage().getId(), page.getId())
                || pageMedia == null
                || !pageMedia.isPreferredOcrInput()
                || pageMedia.getMediaAsset() == null
                || !Objects.equals(pageMedia.getMediaAsset().getId(), input.getId()))
            throw new UnprocessableDocumentEvidenceException(
                    "OCR result does not match the claimed vision job input"
            );

        validateImage(input, pageMedia);

        DocumentPageQualityAssessment deterministic = assessmentRepository
                .findByDocumentPage_IdAndDocumentPageMedia_IdAndAssessmentTypeAndCurrentTrue(
                        page.getId(),
                        pageMedia.getId(),
                        AssessmentType.COMBINED_OCR_QUALITY
                )
                .filter(assessment -> assessment.getDocumentPageOcrResult() != null)
                .filter(assessment -> Objects.equals(
                        assessment.getDocumentPageOcrResult().getId(),
                        ocrResult.getId()
                ))
                .orElseThrow(() -> new UnprocessableDocumentEvidenceException(
                        "A current deterministic assessment is required"
                ));

        if (!Objects.equals(deterministic.getId(), deterministicAssessmentId))
            throw new WorkerManifestConflictException(
                    "Vision-assessment deterministic evidence is no longer current"
            );

        return new Context(job, page, pageMedia, input, ocrResult, deterministic);
    }

    private void validateImage(
            @NonNull MediaAsset input,
            @NonNull DocumentPageMedia pageMedia
    ) {
        if (input.getMediaType() != MediaType.IMAGE
                || input.getMimeType() == null
                || !input.getMimeType().toLowerCase().startsWith("image/"))
            throw new UnprocessableDocumentEvidenceException(
                    "Vision-assessment input is not an image"
            );

        if (input.getSizeBytes() == null
                || input.getSizeBytes() <= 0
                || input.getSizeBytes() > properties.maximumInputSize().toBytes())
            throw new UnprocessableDocumentEvidenceException(
                    "Vision-assessment image size is invalid"
            );

        Integer width = pageMedia.getWidth() == null
                ? input.getWidth()
                : pageMedia.getWidth();
        Integer height = pageMedia.getHeight() == null
                ? input.getHeight()
                : pageMedia.getHeight();

        if (width != null && (width <= 0 || width > properties.maximumPixelWidth()))
            throw new UnprocessableDocumentEvidenceException(
                    "Vision-assessment image width is invalid"
            );

        if (height != null && (height <= 0 || height > properties.maximumPixelHeight()))
            throw new UnprocessableDocumentEvidenceException(
                    "Vision-assessment image height is invalid"
            );

        if (width != null
                && height != null
                && (long) width * height > properties.maximumPagePixels())
            throw new UnprocessableDocumentEvidenceException(
                    "Vision-assessment image pixel count is invalid"
            );
    }

    private WorkerVisionAssessmentCommand validate(
            WorkerVisionAssessmentCommand command,
            String assessedOcrText
    ) {
        if (command == null || command.assessment() == null)
            throw visionInvalid(
                    "VISION_ASSESSMENT_REQUIRED",
                    "Vision assessment metadata is required"
            );

        if (serializedSize(command)
                > properties.maximumVisionAssessmentPayloadSize().toBytes())
            throw new WorkerPayloadTooLargeException(
                    "Vision assessment exceeds the maximum payload size"
            );

        String suggestedText = requiredExact(
                command.suggestedText(),
                properties.maximumVisionSuggestionCharacters(),
                "Suggested text"
        );
        String modelName = safeRequired(
                command.modelName(),
                properties.maximumVisionModelNameCharacters(),
                "Model name"
        );
        String modelVersion = safeRequired(
                command.modelVersion(),
                properties.maximumVisionModelVersionCharacters(),
                "Model version"
        );
        String promptVersion = safeRequired(
                command.promptVersion(),
                properties.maximumVisionPromptVersionCharacters(),
                "Prompt version"
        );
        WorkerQualityAssessmentCommand assessment = validateAssessment(
                command.assessment()
        );

        if (!modelName.equals(assessment.assessorName())
                || !modelVersion.equals(assessment.assessorVersion()))
            throw visionInvalid(
                    "VISION_MODEL_IDENTITY_MISMATCH",
                    "Vision model identity must match the assessor identity"
            );

        if (command.issues() == null
                || command.issues().size() > properties.maximumVisionIssues())
            throw visionInvalid(
                    "VISION_ISSUE_COUNT_INVALID",
                    "Vision issue count is invalid"
            );

        if (command.uncertainPassages() == null
                || command.uncertainPassages().size()
                > properties.maximumVisionUncertainPassages())
            throw visionInvalid(
                    "VISION_UNCERTAIN_PASSAGE_COUNT_INVALID",
                    "Vision uncertain-passage count is invalid"
            );

        List<WorkerVisionIssueCommand> issues = command.issues().stream()
                .map(issue -> validateIssue(issue, assessedOcrText))
                .toList();
        List<WorkerVisionUncertainPassageCommand> uncertainPassages =
                command.uncertainPassages().stream()
                        .map(this::validateUncertainPassage)
                        .toList();

        if ((!issues.isEmpty() || !uncertainPassages.isEmpty())
                && !command.requiresReview())
            throw visionInvalid(
                    "VISION_REVIEW_FLAG_REQUIRED",
                    "Vision results containing issues or uncertain passages require review"
            );

        String issuesJson = issuesJson(issues);
        String uncertainPassagesJson = uncertainPassagesJson(uncertainPassages);

        if (issuesJson.length() > properties.maximumVisionIssuesJsonCharacters())
            throw visionInvalid(
                    "VISION_ISSUES_TOO_LARGE",
                    "Vision issues exceed the maximum stored size"
            );

        if (uncertainPassagesJson.length() > MAXIMUM_STORED_JSON_CHARACTERS)
            throw visionInvalid(
                    "VISION_UNCERTAIN_PASSAGES_TOO_LARGE",
                    "Vision uncertain passages exceed the maximum stored size"
            );

        return new WorkerVisionAssessmentCommand(
                assessment,
                command.requiresReview(),
                suggestedText,
                modelName,
                modelVersion,
                promptVersion,
                issues,
                uncertainPassages
        );
    }

    private WorkerQualityAssessmentCommand validateAssessment(
            WorkerQualityAssessmentCommand command
    ) {
        if (command.overallScore() == null
                || command.overallScore().compareTo(BigDecimal.ZERO) < 0
                || command.overallScore().compareTo(BigDecimal.ONE) > 0
                || command.overallScore().scale() > 4)
            throw new IllegalArgumentException(
                    "Overall score must be between 0 and 1"
            );

        if (command.qualityStatus() == null)
            throw new IllegalArgumentException("Quality status is required");

        if (command.signals() == null
                || command.signals().isEmpty()
                || command.signals().size() > properties.maximumQualitySignals())
            throw new IllegalArgumentException(
                    "Vision assessment signal count is invalid"
            );

        List<WorkerQualitySignalCommand> signals = command.signals().stream()
                .map(this::validateSignal)
                .toList();

        return new WorkerQualityAssessmentCommand(
                safeRequired(command.assessorName(), 100, "Assessor name"),
                safeRequired(command.assessorVersion(), 100, "Assessor version"),
                safeRequired(command.scoreVersion(), 50, "Score version"),
                command.overallScore(),
                command.qualityStatus(),
                safeOptional(
                        command.summary(),
                        properties.maximumQualitySummaryCharacters(),
                        "Summary"
                ),
                safeOptional(
                        command.limitations(),
                        properties.maximumQualityLimitationsCharacters(),
                        "Limitations"
                ),
                signals
        );
    }

    private WorkerQualitySignalCommand validateSignal(
            WorkerQualitySignalCommand signal
    ) {
        if (signal == null || signal.severity() == null)
            throw new IllegalArgumentException("Quality signal is invalid");

        if (signal.decimalValue() != null
                && (signal.decimalValue().precision() > 9
                || signal.decimalValue().scale() > 6))
            throw new IllegalArgumentException(
                    "Signal decimal value exceeds database precision"
            );

        if (signal.weight() != null
                && (signal.weight().compareTo(BigDecimal.ZERO) < 0
                || signal.weight().compareTo(BigDecimal.ONE) > 0
                || signal.weight().scale() > 4))
            throw new IllegalArgumentException(
                    "Signal weight must be between 0 and 1"
            );

        String text = safeOptional(
                signal.textValue(),
                properties.maximumQualitySignalTextCharacters(),
                "Signal text"
        );
        String message = safeOptional(
                signal.safeMessage(),
                properties.maximumQualityMessageCharacters(),
                "Signal message"
        );

        if (signal.decimalValue() == null && text == null && message == null)
            throw new IllegalArgumentException(
                    "Quality signal must contain a value or safe message"
            );

        return new WorkerQualitySignalCommand(
                safeRequired(
                        signal.type(),
                        properties.maximumQualitySignalTypeCharacters(),
                        "Signal type"
                ),
                signal.decimalValue(),
                text,
                signal.severity(),
                signal.weight(),
                message
        );
    }

    private WorkerVisionIssueCommand validateIssue(
            WorkerVisionIssueCommand issue,
            String assessedOcrText
    ) {
        if (issue == null)
            throw visionInvalid(
                    "VISION_ISSUE_REQUIRED",
                    "Vision issue is required"
            );

        String originalText = optionalExact(
                issue.originalText(),
                properties.maximumVisionExcerptCharacters(),
                "Issue original text"
        );
        String suggestedText = optionalExact(
                issue.suggestedText(),
                properties.maximumVisionExcerptCharacters(),
                "Issue suggested text"
        );
        Integer startOffset = issue.startOffset();
        Integer endOffset = issue.endOffset();

        if ((startOffset == null) != (endOffset == null))
            throw visionInvalid(
                    "VISION_ISSUE_OFFSETS_INVALID",
                    "Issue offsets must either both be present or both be absent"
            );

        if (startOffset != null && (assessedOcrText == null
                || originalText == null
                || startOffset < 0
                || endOffset <= startOffset
                || endOffset > assessedOcrText.length()))
            throw visionInvalid(
                    "VISION_ISSUE_OFFSETS_INVALID",
                    "Issue offsets are outside the assessed OCR text"
            );

        if (startOffset != null
                && !assessedOcrText.substring(startOffset, endOffset)
                .equals(originalText))
            throw visionInvalid(
                    "VISION_ISSUE_TEXT_MISMATCH",
                    "Issue text does not match the assessed OCR snapshot"
            );

        boolean safelyApplicable = issue.safelyApplicable()
                || (startOffset != null
                && originalText != null
                && suggestedText != null);

        if (safelyApplicable
                && (startOffset == null
                || originalText == null
                || suggestedText == null))
            throw visionInvalid(
                    "VISION_ISSUE_NOT_APPLICABLE",
                    "Safely applicable issues require exact text and offsets"
            );

        return new WorkerVisionIssueCommand(
                safeRequired(
                        issue.issueType(),
                        properties.maximumVisionIssueCodeCharacters(),
                        "Issue type"
                ),
                safeRequired(
                        issue.explanationBg(),
                        properties.maximumVisionReasonCharacters(),
                        "Issue explanation"
                ),
                confidence(issue.confidence(), "Issue confidence"),
                originalText,
                optionalExact(
                        issue.originalContext(),
                        properties.maximumVisionExcerptCharacters(),
                        "Issue original context"
                ),
                suggestedText,
                optionalExact(
                        issue.suggestedContext(),
                        properties.maximumVisionExcerptCharacters(),
                        "Issue suggested context"
                ),
                startOffset,
                endOffset,
                safelyApplicable
        );
    }

    private WorkerVisionUncertainPassageCommand validateUncertainPassage(
            WorkerVisionUncertainPassageCommand passage
    ) {
        if (passage == null)
            throw visionInvalid(
                    "VISION_UNCERTAIN_PASSAGE_REQUIRED",
                    "Vision uncertain passage is required"
            );

        return new WorkerVisionUncertainPassageCommand(
                requiredExact(
                        passage.excerpt(),
                        properties.maximumVisionExcerptCharacters(),
                        "Uncertain passage excerpt"
                ),
                safeRequired(
                        passage.reason(),
                        properties.maximumVisionReasonCharacters(),
                        "Uncertain passage reason"
                ),
                confidence(passage.confidence(), "Uncertain passage confidence")
        );
    }

    private WorkerVisionAssessmentDetails create(
            @NonNull Context context,
            @NonNull WorkerVisionAssessmentCommand command
    ) {
        WorkerQualityAssessmentCommand quality = command.assessment();
        DocumentPageQualityAssessment assessment = new DocumentPageQualityAssessment();
        assessment.setDocumentPage(context.page());
        assessment.setDocumentPageMedia(context.pageMedia());
        assessment.setProcessingJob(context.job());
        assessment.setDocumentPageOcrResult(context.ocrResult());
        assessment.setAssessmentType(ASSESSMENT_TYPE);
        assessment.setAssessorType(AssessorType.VISION_MODEL);
        assessment.setAssessorName(quality.assessorName());
        assessment.setAssessorVersion(quality.assessorVersion());
        assessment.setScoreVersion(quality.scoreVersion());
        assessment.setQualityStatus(quality.qualityStatus().toDomainStatus());
        assessment.setOverallScore(quality.overallScore());
        assessment.setSummary(quality.summary());
        assessment.setLimitations(quality.limitations());
        assessment.setCurrent(false);

        DocumentPageQualityAssessment savedAssessment =
                assessmentRepository.saveAndFlush(assessment);
        signalRepository.saveAllAndFlush(
                toSignals(savedAssessment, quality.signals())
        );

        DocumentPageTextSuggestion suggestion = new DocumentPageTextSuggestion();
        suggestion.setDocumentPage(context.page());
        suggestion.setDocumentPageMedia(context.pageMedia());
        suggestion.setDocumentPageOcrResult(context.ocrResult());
        suggestion.setProcessingJob(context.job());
        suggestion.setSuggestedText(command.suggestedText());
        suggestion.setSuggestedTextHash(
                ContentHashUtils.sha256(command.suggestedText())
        );
        suggestion.setModelName(command.modelName());
        suggestion.setModelVersion(command.modelVersion());
        suggestion.setPromptVersion(command.promptVersion());
        suggestion.setRequiresReview(command.requiresReview());
        suggestion.setIssuesJson(issuesJson(command.issues()));
        suggestion.setUncertainPassagesJson(
                uncertainPassagesJson(command.uncertainPassages())
        );

        DocumentPageTextSuggestion savedSuggestion =
                suggestionRepository.saveAndFlush(suggestion);
        return toDetails(savedAssessment, savedSuggestion, context, false);
    }

    private List<DocumentPageQualitySignal> toSignals(
            DocumentPageQualityAssessment assessment,
            List<WorkerQualitySignalCommand> commands
    ) {
        List<DocumentPageQualitySignal> signals = new ArrayList<>(commands.size());

        for (int index = 0; index < commands.size(); index++) {
            WorkerQualitySignalCommand command = commands.get(index);
            DocumentPageQualitySignal signal = new DocumentPageQualitySignal();
            signal.setAssessment(assessment);
            signal.setSignalType(command.type());
            signal.setSignalOrdinal(index + 1);
            signal.setSignalValueDecimal(command.decimalValue());
            signal.setSignalValueText(command.textValue());
            signal.setSeverity(command.severity());
            signal.setWeight(command.weight());
            signal.setMessage(command.safeMessage());
            signals.add(signal);
        }

        return signals;
    }

    private WorkerVisionAssessmentDetails existing(
            DocumentPageTextSuggestion suggestion,
            Context context,
            WorkerVisionAssessmentCommand command
    ) {
        DocumentPageQualityAssessment assessment = assessmentRepository
                .findByProcessingJob_Id(context.job().getId())
                .orElseThrow(WorkerVisionAssessmentConflictException::new);
        List<DocumentPageQualitySignal> signals = signalRepository
                .findByAssessment_IdOrderBySignalOrdinalAscIdAsc(assessment.getId());

        if (!matches(assessment, suggestion, signals, context, command))
            throw new WorkerVisionAssessmentConflictException();

        return toDetails(assessment, suggestion, context, true);
    }

    private boolean matches(
            DocumentPageQualityAssessment assessment,
            DocumentPageTextSuggestion suggestion,
            List<DocumentPageQualitySignal> signals,
            Context context,
            WorkerVisionAssessmentCommand command
    ) {
        WorkerQualityAssessmentCommand quality = command.assessment();

        if (assessment.getAssessmentType() != ASSESSMENT_TYPE
                || assessment.getAssessorType() != AssessorType.VISION_MODEL
                || assessment.getDocumentPageOcrResult() == null
                || !Objects.equals(
                        assessment.getDocumentPageOcrResult().getId(),
                        context.ocrResult().getId()
                )
                || !Objects.equals(assessment.getAssessorName(), quality.assessorName())
                || !Objects.equals(assessment.getAssessorVersion(), quality.assessorVersion())
                || !Objects.equals(assessment.getScoreVersion(), quality.scoreVersion())
                || assessment.getQualityStatus() != quality.qualityStatus().toDomainStatus()
                || !equalDecimal(assessment.getOverallScore(), quality.overallScore())
                || !Objects.equals(assessment.getSummary(), quality.summary())
                || !Objects.equals(assessment.getLimitations(), quality.limitations())
                || signals.size() != quality.signals().size()
                || !Objects.equals(suggestion.getSuggestedText(), command.suggestedText())
                || !Objects.equals(suggestion.getSuggestedTextHash(),
                        ContentHashUtils.sha256(command.suggestedText()))
                || !Objects.equals(suggestion.getModelName(), command.modelName())
                || !Objects.equals(suggestion.getModelVersion(), command.modelVersion())
                || !Objects.equals(suggestion.getPromptVersion(), command.promptVersion())
                || suggestion.isRequiresReview() != command.requiresReview()
                || !Objects.equals(suggestion.getIssuesJson(), issuesJson(command.issues()))
                || !Objects.equals(
                        suggestion.getUncertainPassagesJson(),
                        uncertainPassagesJson(command.uncertainPassages())
                ))
            return false;

        for (int index = 0; index < signals.size(); index++) {
            DocumentPageQualitySignal signal = signals.get(index);
            WorkerQualitySignalCommand submitted = quality.signals().get(index);

            if (!Objects.equals(signal.getSignalOrdinal(), index + 1)
                    || !Objects.equals(signal.getSignalType(), submitted.type())
                    || !equalDecimal(signal.getSignalValueDecimal(), submitted.decimalValue())
                    || !Objects.equals(signal.getSignalValueText(), submitted.textValue())
                    || signal.getSeverity() != submitted.severity()
                    || !equalDecimal(signal.getWeight(), submitted.weight())
                    || !Objects.equals(signal.getMessage(), submitted.safeMessage()))
                return false;
        }

        return true;
    }

    private WorkerVisionAssessmentContextDetails toContextDetails(
            Context context,
            List<DocumentPageQualitySignal> signals
    ) {
        DocumentPageMedia pageMedia = context.pageMedia();
        MediaAsset input = context.input();
        DocumentPageQualityAssessment deterministic =
                context.deterministicAssessment();

        return new WorkerVisionAssessmentContextDetails(
                context.job().getId(),
                context.page().getDocument().getId(),
                context.page().getId(),
                context.ocrResult().getId(),
                pageMedia.getId(),
                input.getId(),
                context.ocrResult().getRawText(),
                context.ocrResult().getOcrConfidence(),
                context.ocrResult().getOcrLanguage(),
                context.ocrResult().getStructuredOutputJson(),
                deterministic.getId(),
                context.page().getTranscriptionApprovalState(),
                context.page().getReviewState(),
                context.page().getIndexingState(),
                deterministic.getQualityStatus(),
                deterministic.getOverallScore(),
                deterministic.getSummary(),
                deterministic.getLimitations(),
                signals.stream().map(this::toSignalDetails).toList(),
                input.getMimeType(),
                input.getSizeBytes(),
                pageMedia.getWidth() == null ? input.getWidth() : pageMedia.getWidth(),
                pageMedia.getHeight() == null ? input.getHeight() : pageMedia.getHeight(),
                pageMedia.getDpi(),
                pageMedia.getColorMode()
        );
    }

    private WorkerVisionQualitySignalDetails toSignalDetails(
            DocumentPageQualitySignal signal
    ) {
        return new WorkerVisionQualitySignalDetails(
                signal.getSignalType(),
                signal.getSignalValueDecimal(),
                signal.getSignalValueText(),
                signal.getSeverity(),
                signal.getWeight(),
                signal.getMessage()
        );
    }

    private WorkerVisionAssessmentDetails toDetails(
            DocumentPageQualityAssessment assessment,
            DocumentPageTextSuggestion suggestion,
            Context context,
            boolean existing
    ) {
        return new WorkerVisionAssessmentDetails(
                assessment.getId(),
                suggestion.getId(),
                context.job().getId(),
                context.page().getDocument().getId(),
                context.page().getId(),
                context.ocrResult().getId(),
                context.input().getId(),
                existing
        );
    }

    private String issuesJson(List<WorkerVisionIssueCommand> issues) {
        try {
            return objectMapper.writeValueAsString(issues);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Vision issues cannot be serialized", ex);
        }
    }

    private String uncertainPassagesJson(
            List<WorkerVisionUncertainPassageCommand> uncertainPassages
    ) {
        try {
            return objectMapper.writeValueAsString(uncertainPassages);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException(
                    "Vision uncertain passages cannot be serialized",
                    ex
            );
        }
    }

    public List<WorkerVisionIssueCommand> parseIssues(String issuesJson) {
        try {
            return objectMapper.readValue(
                    issuesJson,
                    new TypeReference<>() { }
            );
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Stored vision issues are invalid", ex);
        }
    }

    public List<WorkerVisionUncertainPassageCommand> parseUncertainPassages(
            String uncertainPassagesJson
    ) {
        try {
            return objectMapper.readValue(
                    uncertainPassagesJson,
                    new TypeReference<>() { }
            );
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(
                    "Stored vision uncertain passages are invalid",
                    ex
            );
        }
    }

    private long serializedSize(Object value) {
        try {
            return objectMapper.writeValueAsBytes(value).length;
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Worker payload cannot be serialized", ex);
        }
    }

    private String requiredExact(String value, int maximumLength, String field) {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException(field + " is required");

        if (value.length() > maximumLength)
            throw new IllegalArgumentException(field + " exceeds the maximum length");

        if (value.chars().anyMatch(character ->
                Character.isISOControl(character)
                        && character != '\n'
                        && character != '\r'
                        && character != '\t'))
            throw new IllegalArgumentException(field + " contains invalid control characters");

        return value;
    }

    private String safeRequired(String value, int maximumLength, String field) {
        String sanitized = safeOptional(value, maximumLength, field);

        if (sanitized == null)
            throw new IllegalArgumentException(field + " is required");

        return sanitized;
    }

    private String optionalExact(String value, int maximumLength, String field) {
        if (value == null)
            return null;

        if (value.isBlank())
            throw new IllegalArgumentException(field + " cannot be blank");

        if (value.length() > maximumLength)
            throw new IllegalArgumentException(field + " exceeds the maximum length");

        if (value.chars().anyMatch(character ->
                Character.isISOControl(character)
                        && character != '\n'
                        && character != '\r'
                        && character != '\t'))
            throw new IllegalArgumentException(field + " contains invalid control characters");

        return value;
    }

    private String safeOptional(String value, int maximumLength, String field) {
        if (value == null)
            return null;

        String sanitized = value
                .replaceAll("[\\p{Cc}&&[^\\r\\n\\t]]", "")
                .trim();

        if (sanitized.isEmpty())
            return null;

        if (sanitized.length() > maximumLength)
            throw new IllegalArgumentException(field + " exceeds the maximum length");

        return sanitized;
    }

    private boolean equalDecimal(BigDecimal left, BigDecimal right) {
        if (left == null || right == null)
            return left == right;

        return left.compareTo(right) == 0;
    }

    private BigDecimal confidence(BigDecimal value, String field) {
        if (value == null
                || value.compareTo(BigDecimal.ZERO) < 0
                || value.compareTo(BigDecimal.ONE) > 0
                || value.scale() > 4)
            throw new IllegalArgumentException(
                    field + " must be between 0 and 1 with at most four decimal places"
            );

        return value.setScale(4);
    }

    private WorkerVisionValidationException visionInvalid(
            String code,
            String message
    ) {
        return new WorkerVisionValidationException(code, message);
    }

    private record Context(
            DocumentProcessingJob job,
            DocumentPage page,
            DocumentPageMedia pageMedia,
            MediaAsset input,
            DocumentPageOcrResult ocrResult,
            DocumentPageQualityAssessment deterministicAssessment
    ) {
    }
}
