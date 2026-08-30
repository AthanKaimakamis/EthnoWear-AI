package fmi.ethnowear.application.service.worker.job;

import fmi.ethnowear.application.dto.worker.ocr.WorkerOcrResultCommand;
import fmi.ethnowear.application.dto.worker.ocr.WorkerOcrResultDetails;
import fmi.ethnowear.application.exception.ResourceNotFoundException;
import fmi.ethnowear.application.exception.UnprocessableDocumentEvidenceException;
import fmi.ethnowear.application.exception.WorkerClaimConflictException;
import fmi.ethnowear.application.exception.WorkerOcrResultConflictException;
import fmi.ethnowear.application.exception.WorkerPayloadTooLargeException;
import fmi.ethnowear.application.model.worker.WorkerClaimCredentials;
import fmi.ethnowear.application.service.document.ocr.OcrResultImportValidator;
import fmi.ethnowear.application.service.worker.security.WorkerClaimedJobLoader;
import fmi.ethnowear.config.WorkerApiProperties;
import fmi.ethnowear.config.FigureExtractionProperties;
import fmi.ethnowear.application.dto.worker.figure.WorkerFigureCandidateCommand;
import fmi.ethnowear.application.service.document.figure.FigureBoundsValidator;
import fmi.ethnowear.domain.model.document.figure.FigureExtractionState;
import fmi.ethnowear.domain.model.archive.MediaType;
import fmi.ethnowear.domain.model.document.processing.JobStatus;
import fmi.ethnowear.domain.model.document.processing.JobType;
import fmi.ethnowear.persistence.jpa.entity.MediaAsset;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPage;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPageMedia;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPageOcrResult;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPageFigureCandidate;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentProcessingJob;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageMediaRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageOcrResultRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageFigureCandidateRepository;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.HashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WorkerOcrResultService {

    private final WorkerClaimedJobLoader jobLoader;
    private final DocumentPageMediaRepository pageMediaRepository;
    private final DocumentPageOcrResultRepository ocrResultRepository;
    private final DocumentPageFigureCandidateRepository figureCandidateRepository;
    private final OcrResultImportValidator validator;
    private final WorkerApiProperties properties;
    private final FigureExtractionProperties figureProperties;
    private final FigureBoundsValidator figureBoundsValidator;

    @Transactional
    public WorkerOcrResultDetails accept(
            Long jobId,
            WorkerClaimCredentials credentials,
            WorkerOcrResultCommand command
    ) {
        if (jobId == null)
            throw new IllegalArgumentException("Job id is required");

        WorkerOcrResultCommand validated = validate(command);
        DocumentProcessingJob job = jobLoader.requireActive(jobId, credentials);

        if (job.getStatus() == JobStatus.CANCEL_REQUESTED)
            throw new WorkerClaimConflictException();

        if (job.getJobType() != JobType.OCR)
            throw new IllegalArgumentException("Job is not an OCR job");

        DocumentPage page = requirePage(job);
        DocumentPageMedia pageMedia = requireInputMedia(job, page);

        return ocrResultRepository.findByProcessingJob_Id(jobId)
                .map(existing -> existingResult(existing, job, page, pageMedia, validated))
                .orElseGet(() -> createResult(job, page, pageMedia, validated));
    }

    private WorkerOcrResultCommand validate(WorkerOcrResultCommand command) {
        if (command == null)
            throw new IllegalArgumentException("OCR result command is required");

        if (command.rawText() != null
                && command.rawText().length() > properties.maximumOcrTextCharacters())
            throw new WorkerPayloadTooLargeException(
                    "OCR text exceeds the maximum character count"
            );

        long outputBytes = utf8Length(command.parametersJson())
                + utf8Length(command.structuredOutputJson());

        if (outputBytes > properties.maximumOcrOutputSize().toBytes())
            throw new WorkerPayloadTooLargeException(
                    "OCR structured output exceeds the maximum size"
            );

        validator.validate(command);
        validateFigureCandidates(command.figureCandidates());
        return command;
    }

    private void validateFigureCandidates(List<WorkerFigureCandidateCommand> candidates) {
        if (candidates.size() > figureProperties.maximumCandidates())
            throw new WorkerPayloadTooLargeException(
                    "Figure candidates exceed the configured maximum"
            );

        HashSet<Integer> ordinals = new HashSet<>();

        for (WorkerFigureCandidateCommand candidate : candidates) {
            if (candidate == null)
                throw new IllegalArgumentException("Figure candidate is required");

            if (!ordinals.add(candidate.candidateOrdinal()))
                throw new IllegalArgumentException("Figure candidate ordinals must be unique");

            figureBoundsValidator.validate(
                    candidate.normalizedX(),
                    candidate.normalizedY(),
                    candidate.normalizedWidth(),
                    candidate.normalizedHeight()
            );

            if (candidate.rawCaptionText() != null
                    && candidate.rawCaptionText().trim().length()
                    > figureProperties.maximumCaptionCharacters())
                throw new IllegalArgumentException("Figure caption exceeds the configured maximum");
        }
    }

    private long utf8Length(String value) {
        return value == null ? 0 : value.getBytes(StandardCharsets.UTF_8).length;
    }

    private @NonNull DocumentPage requirePage(@NonNull DocumentProcessingJob job) {
        DocumentPage page = job.getDocumentPage();

        if (page == null || page.getDocument() == null)
            throw new UnprocessableDocumentEvidenceException("OCR job has no valid document page");

        if (job.getDocument() != null
                && !Objects.equals(job.getDocument().getId(), page.getDocument().getId()))
            throw new UnprocessableDocumentEvidenceException("OCR job document and page are inconsistent");

        return page;
    }

    private @NonNull DocumentPageMedia requireInputMedia(
            @NonNull DocumentProcessingJob job,
            @NonNull DocumentPage page
    ) {
        MediaAsset input = job.getInputMediaAsset();

        if (input == null)
            throw new ResourceNotFoundException("Job input media", job.getId());

        DocumentPageMedia pageMedia = pageMediaRepository
                .findByDocumentPage_IdAndPreferredOcrInputTrue(page.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Preferred OCR input",
                        page.getId()
                ));

        if (pageMedia.getMediaAsset() == null
                || !Objects.equals(pageMedia.getMediaAsset().getId(), input.getId()))
            throw new UnprocessableDocumentEvidenceException(
                    "OCR job input is not the page's preferred rendition"
            );

        if (input.getMediaType() != MediaType.IMAGE
                || input.getMimeType() == null
                || !input.getMimeType().toLowerCase().startsWith("image/"))
            throw new UnprocessableDocumentEvidenceException("OCR input is not a supported image");

        if (input.getSizeBytes() == null
                || input.getSizeBytes() <= 0
                || input.getSizeBytes() > properties.maximumInputSize().toBytes())
            throw new UnprocessableDocumentEvidenceException("OCR input size is invalid");

        return pageMedia;
    }

    private WorkerOcrResultDetails existingResult(
            @NonNull DocumentPageOcrResult existing,
            DocumentProcessingJob job,
            DocumentPage page,
            DocumentPageMedia pageMedia,
            WorkerOcrResultCommand command
    ) {
        if (!matches(existing, command))
            throw new WorkerOcrResultConflictException();

        return toDetails(existing, job, page, pageMedia, true);
    }

    private WorkerOcrResultDetails createResult(
            DocumentProcessingJob job,
            DocumentPage page,
            DocumentPageMedia pageMedia,
            WorkerOcrResultCommand command
    ) {
        DocumentPageOcrResult result = new DocumentPageOcrResult();
        result.setDocumentPage(page);
        result.setDocumentPageMedia(pageMedia);
        result.setProcessingJob(job);
        result.setRawText(command.rawText());
        result.setOcrEngine(command.ocrEngine().trim());
        result.setOcrEngineVersion(command.ocrEngineVersion());
        result.setOcrLanguage(command.ocrLanguage());
        result.setOcrConfidence(command.ocrConfidence());
        result.setParametersJson(command.parametersJson());
        result.setStructuredOutputJson(command.structuredOutputJson());
        result.setCurrent(false);

        DocumentPageOcrResult saved = ocrResultRepository.saveAndFlush(result);
        saveCandidates(saved, pageMedia, command.figureCandidates());

        return toDetails(
                saved,
                job,
                page,
                pageMedia,
                false
        );
    }

    private boolean matches(
            @NonNull DocumentPageOcrResult existing,
            @NonNull WorkerOcrResultCommand command
    ) {
        return Objects.equals(existing.getRawText(), command.rawText())
                && Objects.equals(existing.getOcrEngine(), command.ocrEngine().trim())
                && Objects.equals(existing.getOcrEngineVersion(), command.ocrEngineVersion())
                && Objects.equals(existing.getOcrLanguage(), command.ocrLanguage())
                && equalDecimal(existing.getOcrConfidence(), command.ocrConfidence())
                && Objects.equals(existing.getParametersJson(), command.parametersJson())
                && Objects.equals(existing.getStructuredOutputJson(), command.structuredOutputJson())
                && candidatesMatch(existing.getId(), command.figureCandidates());
    }

    private void saveCandidates(
            DocumentPageOcrResult result,
            DocumentPageMedia pageMedia,
            List<WorkerFigureCandidateCommand> candidates
    ) {
        List<DocumentPageFigureCandidate> entities = candidates.stream()
                .map(candidate -> candidate(result, pageMedia, candidate))
                .toList();

        if (!entities.isEmpty())
            figureCandidateRepository.saveAllAndFlush(entities);

        result.setFigureExtractionState(
                entities.isEmpty()
                        ? FigureExtractionState.NOT_REQUESTED
                        : FigureExtractionState.PENDING
        );
        result.setFigureExtractionMessage(null);
        ocrResultRepository.saveAndFlush(result);
    }

    private DocumentPageFigureCandidate candidate(
            DocumentPageOcrResult result,
            DocumentPageMedia pageMedia,
            WorkerFigureCandidateCommand command
    ) {
        DocumentPageFigureCandidate candidate = new DocumentPageFigureCandidate();
        candidate.setDocumentPageOcrResult(result);
        candidate.setDocumentPageMedia(pageMedia);
        candidate.setCandidateOrdinal(command.candidateOrdinal());
        candidate.setNormalizedX(command.normalizedX());
        candidate.setNormalizedY(command.normalizedY());
        candidate.setNormalizedWidth(command.normalizedWidth());
        candidate.setNormalizedHeight(command.normalizedHeight());
        candidate.setRawCaptionText(normalize(command.rawCaptionText()));
        candidate.setDetectionConfidence(command.detectionConfidence());
        return candidate;
    }

    private boolean candidatesMatch(
            Long resultId,
            List<WorkerFigureCandidateCommand> commands
    ) {
        List<DocumentPageFigureCandidate> existing = figureCandidateRepository
                .findByDocumentPageOcrResult_IdOrderByCandidateOrdinalAsc(resultId);

        if (existing.size() != commands.size())
            return false;

        List<WorkerFigureCandidateCommand> ordered = commands.stream()
                .sorted(java.util.Comparator.comparing(
                        WorkerFigureCandidateCommand::candidateOrdinal
                ))
                .toList();

        for (int i = 0; i < existing.size(); i++) {
            DocumentPageFigureCandidate candidate = existing.get(i);
            WorkerFigureCandidateCommand command = ordered.get(i);

            if (!Objects.equals(candidate.getCandidateOrdinal(), command.candidateOrdinal())
                    || !equalDecimal(candidate.getNormalizedX(), command.normalizedX())
                    || !equalDecimal(candidate.getNormalizedY(), command.normalizedY())
                    || !equalDecimal(candidate.getNormalizedWidth(), command.normalizedWidth())
                    || !equalDecimal(candidate.getNormalizedHeight(), command.normalizedHeight())
                    || !Objects.equals(candidate.getRawCaptionText(), normalize(command.rawCaptionText()))
                    || !equalDecimal(candidate.getDetectionConfidence(), command.detectionConfidence()))
                return false;
        }

        return true;
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private boolean equalDecimal(BigDecimal left, BigDecimal right) {
        if (left == null || right == null)
            return left == right;

        return left.compareTo(right) == 0;
    }

    private WorkerOcrResultDetails toDetails(
            DocumentPageOcrResult result,
            DocumentProcessingJob job,
            DocumentPage page,
            DocumentPageMedia pageMedia,
            boolean existing
    ) {
        return new WorkerOcrResultDetails(
                result.getId(),
                job.getId(),
                page.getDocument().getId(),
                page.getId(),
                pageMedia.getMediaAsset().getId(),
                existing
        );
    }
}
