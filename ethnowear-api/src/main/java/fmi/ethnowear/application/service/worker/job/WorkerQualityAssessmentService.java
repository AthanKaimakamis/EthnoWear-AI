package fmi.ethnowear.application.service.worker.job;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fmi.ethnowear.application.dto.worker.quality.*;
import fmi.ethnowear.application.exception.ResourceNotFoundException;
import fmi.ethnowear.application.exception.UnprocessableDocumentEvidenceException;
import fmi.ethnowear.application.exception.WorkerClaimConflictException;
import fmi.ethnowear.application.exception.WorkerPayloadTooLargeException;
import fmi.ethnowear.application.exception.WorkerQualityAssessmentConflictException;
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
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageOcrResultRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageQualityAssessmentRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageQualitySignalRepository;
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
public class WorkerQualityAssessmentService {

    private static final AssessmentType ASSESSMENT_TYPE = AssessmentType.COMBINED_OCR_QUALITY;

    private final WorkerClaimedJobLoader jobLoader;
    private final DocumentPageOcrResultRepository ocrResultRepository;
    private final DocumentPageQualityAssessmentRepository assessmentRepository;
    private final DocumentPageQualitySignalRepository signalRepository;
    private final WorkerApiProperties properties;
    private final ObjectMapper objectMapper;
    private final DocumentProcessingJobKeyFactory jobKeyFactory;

    @Transactional
    public WorkerQualityAssessmentContextDetails context(
            Long jobId,
            WorkerClaimCredentials credentials
    ) {
        Context context = requireContext(jobId, credentials);
        WorkerQualityAssessmentContextDetails details = toContextDetails(context);

        if (serializedSize(details) > properties.maximumOcrContextSize().toBytes())
            throw new WorkerPayloadTooLargeException("OCR quality context exceeds the maximum size");

        return details;
    }

    @Transactional
    public WorkerQualityAssessmentDetails accept(
            Long jobId,
            WorkerClaimCredentials credentials,
            WorkerQualityAssessmentCommand command
    ) {
        WorkerQualityAssessmentCommand validated = validate(command);
        Context context = requireContext(jobId, credentials);

        return assessmentRepository.findByProcessingJob_Id(jobId)
                .map(existing -> existing(existing, context, validated))
                .orElseGet(() -> create(context, validated));
    }

    private Context requireContext(Long jobId, WorkerClaimCredentials credentials) {
        DocumentProcessingJob job = jobLoader.requireActive(jobId, credentials);

        if (job.getStatus() == JobStatus.CANCEL_REQUESTED)
            throw new WorkerClaimConflictException();

        if (job.getJobType() != JobType.OCR_QUALITY_ASSESSMENT)
            throw new IllegalArgumentException("Job is not an OCR quality-assessment job");

        DocumentPage page = job.getDocumentPage();

        if (page == null || page.getDocument() == null)
            throw new UnprocessableDocumentEvidenceException("Quality-assessment job has no valid page");

        if (job.getDocument() != null
                && !Objects.equals(job.getDocument().getId(), page.getDocument().getId()))
            throw new UnprocessableDocumentEvidenceException("Quality-assessment job document and page are inconsistent");

        MediaAsset input = job.getInputMediaAsset();

        if (input == null)
            throw new ResourceNotFoundException("Job input media", job.getId());

        Long ocrResultId = jobKeyFactory.ocrResultId(job.getJobKey());
        DocumentPageOcrResult ocrResult = ocrResultRepository
                .findById(ocrResultId)
                .orElseThrow(() -> new ResourceNotFoundException("OCR result", ocrResultId));

        DocumentPageMedia pageMedia = ocrResult.getDocumentPageMedia();

        if (ocrResult.getDocumentPage() == null
                || !Objects.equals(ocrResult.getDocumentPage().getId(), page.getId())
                || pageMedia == null
                || pageMedia.getMediaAsset() == null
                || !Objects.equals(pageMedia.getMediaAsset().getId(), input.getId()))
            throw new UnprocessableDocumentEvidenceException(
                    "OCR result does not match the claimed job input"
            );

        validateImage(input, pageMedia);
        return new Context(job, page, pageMedia, input, ocrResult);
    }

    private void validateImage(
            @NonNull MediaAsset input,
            @NonNull DocumentPageMedia pageMedia
    ) {
        if (input.getMediaType() != MediaType.IMAGE
                || input.getMimeType() == null
                || !input.getMimeType().toLowerCase().startsWith("image/"))
            throw new UnprocessableDocumentEvidenceException("Quality-assessment input is not an image");

        if (input.getSizeBytes() == null
                || input.getSizeBytes() <= 0
                || input.getSizeBytes() > properties.maximumInputSize().toBytes())
            throw new UnprocessableDocumentEvidenceException("Quality-assessment image size is invalid");

        Integer width = pageMedia.getWidth() == null ? input.getWidth() : pageMedia.getWidth();
        Integer height = pageMedia.getHeight() == null ? input.getHeight() : pageMedia.getHeight();

        if (width != null && (width <= 0 || width > properties.maximumPixelWidth()))
            throw new UnprocessableDocumentEvidenceException("Quality-assessment image width is invalid");

        if (height != null && (height <= 0 || height > properties.maximumPixelHeight()))
            throw new UnprocessableDocumentEvidenceException("Quality-assessment image height is invalid");

        if (width != null
                && height != null
                && (long) width * height > properties.maximumPagePixels())
            throw new UnprocessableDocumentEvidenceException("Quality-assessment image pixel count is invalid");
    }

    private WorkerQualityAssessmentCommand validate(WorkerQualityAssessmentCommand command) {
        if (command == null)
            throw new IllegalArgumentException("Quality assessment is required");

        if (serializedSize(command) > properties.maximumQualityAssessmentPayloadSize().toBytes())
            throw new WorkerPayloadTooLargeException("Quality assessment exceeds the maximum payload size");

        if (command.signals() == null
                || command.signals().isEmpty()
                || command.signals().size() > properties.maximumQualitySignals())
            throw new IllegalArgumentException("Quality assessment signal count is invalid");

        if (command.qualityStatus() == null)
            throw new IllegalArgumentException("Quality status is required");

        requireUnitDecimal(command.overallScore(), 4, "Overall score");

        String assessorName = required(command.assessorName(), 100, "Assessor name");
        String assessorVersion = required(command.assessorVersion(), 100, "Assessor version");
        String scoreVersion = required(command.scoreVersion(), 50, "Score version");
        String summary = optional(
                command.summary(),
                properties.maximumQualitySummaryCharacters(),
                "Summary"
        );
        String limitations = optional(
                command.limitations(),
                properties.maximumQualityLimitationsCharacters(),
                "Limitations"
        );

        List<WorkerQualitySignalCommand> signals = new ArrayList<>(command.signals().size());

        for (WorkerQualitySignalCommand signal : command.signals())
            signals.add(validate(signal));

        return new WorkerQualityAssessmentCommand(
                assessorName,
                assessorVersion,
                scoreVersion,
                command.overallScore(),
                command.qualityStatus(),
                summary,
                limitations,
                signals
        );
    }

    private WorkerQualitySignalCommand validate(WorkerQualitySignalCommand signal) {
        if (signal == null)
            throw new IllegalArgumentException("Quality signal is required");

        if (signal.severity() == null)
            throw new IllegalArgumentException("Signal severity is required");

        if (signal.decimalValue() != null
                && (signal.decimalValue().precision() > 9 || signal.decimalValue().scale() > 6))
            throw new IllegalArgumentException("Signal decimal value exceeds database precision");

        if (signal.weight() != null)
            requireUnitDecimal(signal.weight(), 4, "Signal weight");

        String type = required(
                signal.type(),
                properties.maximumQualitySignalTypeCharacters(),
                "Signal type"
        );
        String text = optional(
                signal.textValue(),
                properties.maximumQualitySignalTextCharacters(),
                "Signal text"
        );
        String message = optional(
                signal.safeMessage(),
                properties.maximumQualityMessageCharacters(),
                "Signal message"
        );

        if (signal.decimalValue() == null && text == null && message == null)
            throw new IllegalArgumentException("Quality signal must contain a value or safe message");

        return new WorkerQualitySignalCommand(
                type,
                signal.decimalValue(),
                text,
                signal.severity(),
                signal.weight(),
                message
        );
    }

    private String required(String value, int maximumLength, String field) {
        String sanitized = optional(value, maximumLength, field);

        if (sanitized == null)
            throw new IllegalArgumentException(field + " is required");

        return sanitized;
    }

    private String optional(String value, int maximumLength, String field) {
        if (value == null)
            return null;

        String sanitized = value.replaceAll("[\\p{Cc}&&[^\\r\\n\\t]]", "").trim();

        if (sanitized.isEmpty())
            return null;

        if (sanitized.length() > maximumLength)
            throw new IllegalArgumentException(field + " exceeds the maximum length");

        return sanitized;
    }

    private WorkerQualityAssessmentDetails create(
            @NonNull Context context,
            @NonNull WorkerQualityAssessmentCommand command
    ) {
        DocumentPageQualityAssessment assessment = new DocumentPageQualityAssessment();
        assessment.setDocumentPage(context.page());
        assessment.setDocumentPageMedia(context.pageMedia());
        assessment.setProcessingJob(context.job());
        assessment.setDocumentPageOcrResult(context.ocrResult());
        assessment.setAssessmentType(ASSESSMENT_TYPE);
        assessment.setAssessorType(AssessorType.DETERMINISTIC);
        assessment.setAssessorName(command.assessorName());
        assessment.setAssessorVersion(command.assessorVersion());
        assessment.setScoreVersion(command.scoreVersion());
        assessment.setQualityStatus(command.qualityStatus().toDomainStatus());
        assessment.setOverallScore(command.overallScore());
        assessment.setSummary(command.summary());
        assessment.setLimitations(command.limitations());
        assessment.setCurrent(false);

        DocumentPageQualityAssessment saved = assessmentRepository.saveAndFlush(assessment);
        signalRepository.saveAllAndFlush(toSignals(saved, command.signals()));
        return toDetails(saved, context, false);
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

    private WorkerQualityAssessmentDetails existing(
            DocumentPageQualityAssessment assessment,
            Context context,
            WorkerQualityAssessmentCommand command
    ) {
        List<DocumentPageQualitySignal> signals = signalRepository
                .findByAssessment_IdOrderBySignalOrdinalAscIdAsc(assessment.getId());

        if (!matches(assessment, signals, context, command))
            throw new WorkerQualityAssessmentConflictException();

        return toDetails(assessment, context, true);
    }

    private boolean matches(
            @NonNull DocumentPageQualityAssessment assessment,
            @NonNull List<DocumentPageQualitySignal> signals,
            @NonNull Context context,
            @NonNull WorkerQualityAssessmentCommand command
    ) {
        if (assessment.getDocumentPage() == null
                || !Objects.equals(assessment.getDocumentPage().getId(), context.page().getId())
                || assessment.getDocumentPageMedia() == null
                || !Objects.equals(assessment.getDocumentPageMedia().getId(), context.pageMedia().getId())
                || assessment.getDocumentPageOcrResult() == null
                || !Objects.equals(assessment.getDocumentPageOcrResult().getId(), context.ocrResult().getId())
                || assessment.getAssessmentType() != ASSESSMENT_TYPE
                || assessment.getAssessorType() != AssessorType.DETERMINISTIC
                || !Objects.equals(assessment.getAssessorName(), command.assessorName())
                || !Objects.equals(assessment.getAssessorVersion(), command.assessorVersion())
                || !Objects.equals(assessment.getScoreVersion(), command.scoreVersion())
                || assessment.getQualityStatus() != command.qualityStatus().toDomainStatus()
                || !equalDecimal(assessment.getOverallScore(), command.overallScore())
                || !Objects.equals(assessment.getSummary(), command.summary())
                || !Objects.equals(assessment.getLimitations(), command.limitations())
                || signals.size() != command.signals().size())
            return false;

        for (int index = 0; index < signals.size(); index++) {
            DocumentPageQualitySignal signal = signals.get(index);
            WorkerQualitySignalCommand submitted = command.signals().get(index);

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

    private boolean equalDecimal(BigDecimal left, BigDecimal right) {
        if (left == null || right == null)
            return left == right;

        return left.compareTo(right) == 0;
    }

    private void requireUnitDecimal(BigDecimal value, int maximumScale, String field) {
        if (value == null
                || value.compareTo(BigDecimal.ZERO) < 0
                || value.compareTo(BigDecimal.ONE) > 0
                || value.scale() > maximumScale)
            throw new IllegalArgumentException(field + " must be between 0 and 1");
    }

    private WorkerQualityAssessmentContextDetails toContextDetails(@NonNull Context context) {
        DocumentPageMedia pageMedia = context.pageMedia();
        MediaAsset input = context.input();

        return new WorkerQualityAssessmentContextDetails(
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
                input.getMimeType(),
                input.getSizeBytes(),
                pageMedia.getWidth() == null ? input.getWidth() : pageMedia.getWidth(),
                pageMedia.getHeight() == null ? input.getHeight() : pageMedia.getHeight(),
                pageMedia.getDpi(),
                pageMedia.getColorMode()
        );
    }

    private WorkerQualityAssessmentDetails toDetails(
            DocumentPageQualityAssessment assessment,
            Context context,
            boolean existing
    ) {
        return new WorkerQualityAssessmentDetails(
                assessment.getId(),
                context.job().getId(),
                context.page().getDocument().getId(),
                context.page().getId(),
                context.ocrResult().getId(),
                context.input().getId(),
                existing
        );
    }

    private long serializedSize(Object value) {
        try {
            return objectMapper.writeValueAsBytes(value).length;
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Worker payload cannot be serialized", ex);
        }
    }

    private record Context(
            DocumentProcessingJob job,
            DocumentPage page,
            DocumentPageMedia pageMedia,
            MediaAsset input,
            DocumentPageOcrResult ocrResult
    ) {
    }
}
