package fmi.ethnowear.application.service.document.figure;

import fmi.ethnowear.application.dto.document.command.figure.FigureCaptionUpdateCommand;
import fmi.ethnowear.application.dto.document.command.figure.FigureReviewCommand;
import fmi.ethnowear.application.dto.document.query.figure.DocumentPageFigureDetails;
import fmi.ethnowear.application.dto.document.query.history.DocumentProcessingJobDetails;
import fmi.ethnowear.application.exception.DocumentDependencyConflictException;
import fmi.ethnowear.application.exception.ResourceNotFoundException;
import fmi.ethnowear.application.model.event.ManagementEvent;
import fmi.ethnowear.application.service.archive.media.asset.MediaAssetUsageChecker;
import fmi.ethnowear.application.service.archive.media.storage.MediaFileCompensation;
import fmi.ethnowear.application.service.archive.media.storage.MediaPathResolver;
import fmi.ethnowear.application.service.document.processing.DocumentProcessingJobScheduler;
import fmi.ethnowear.application.service.document.query.mapper.DocumentHistoryMapper;
import fmi.ethnowear.application.service.event.ManagementEventPublisher;
import fmi.ethnowear.domain.model.document.figure.FigureExtractionState;
import fmi.ethnowear.domain.model.document.figure.FigureReviewState;
import fmi.ethnowear.persistence.jpa.entity.MediaAsset;
import fmi.ethnowear.persistence.jpa.entity.SourceReference;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPage;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPageFigure;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPageOcrResult;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentProcessingJob;
import fmi.ethnowear.persistence.jpa.repository.MediaAssetRepository;
import fmi.ethnowear.persistence.jpa.repository.SourceReferenceRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageFigureCandidateRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageFigureRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageOcrResultRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;

import static fmi.ethnowear.util.IdentifierUtils.requireId;
import static fmi.ethnowear.util.RowVersionUtils.requireMatch;
import static fmi.ethnowear.util.TextUtils.isBlank;

@Service
@RequiredArgsConstructor
public class DocumentPageFigureAdminService {

    private final DocumentPageRepository pageRepository;
    private final DocumentPageFigureRepository figureRepository;
    private final DocumentPageFigureCandidateRepository candidateRepository;
    private final DocumentPageOcrResultRepository ocrResultRepository;
    private final SourceReferenceRepository sourceReferenceRepository;
    private final MediaAssetRepository mediaAssetRepository;
    private final MediaAssetUsageChecker mediaUsageChecker;
    private final MediaPathResolver paths;
    private final MediaFileCompensation fileCompensation;
    private final DocumentProcessingJobScheduler jobScheduler;
    private final DocumentHistoryMapper historyMapper;
    private final DocumentPageFigureMapper mapper;
    private final ManagementEventPublisher managementEvents;

    @Transactional(readOnly = true)
    public List<DocumentPageFigureDetails> findAll(Long pageId) {
        requirePage(pageId);
        return figureRepository
                .findByDocumentPage_IdOrderByFigureOrdinalAscIdAsc(pageId)
                .stream()
                .map(mapper::toDetails)
                .toList();
    }

    @Transactional(readOnly = true)
    public DocumentPageFigureDetails findById(Long pageId, Long figureId) {
        return mapper.toDetails(requireFigure(pageId, figureId));
    }

    @Transactional
    public DocumentPageFigureDetails update(
            Long pageId,
            Long figureId,
            String version,
            FigureCaptionUpdateCommand command
    ) {
        if (command == null)
            throw new IllegalArgumentException("Figure update command is required");

        DocumentPageFigure figure = requireFigure(pageId, figureId);
        requireMatch(figure.getRowVersion(), version);
        String correctedCaption = normalize(command.correctedCaptionText());
        String printedFigureNumber = normalize(command.printedFigureNumber());
        SourceReference sourceReference = reference(command.sourceReferenceId());

        boolean changed = !Objects.equals(
                figure.getCorrectedCaptionText(),
                correctedCaption
        ) || !Objects.equals(
                figure.getPrintedFigureNumber(),
                printedFigureNumber
        ) || !Objects.equals(
                sourceReferenceId(figure.getSourceReference()),
                sourceReferenceId(sourceReference)
        );

        figure.setCorrectedCaptionText(correctedCaption);
        figure.setPrintedFigureNumber(printedFigureNumber);
        figure.setSourceReference(sourceReference);

        if (changed && figure.getReviewState() != FigureReviewState.PENDING)
            resetReview(figure);

        DocumentPageFigure saved = figureRepository.saveAndFlush(figure);
        managementEvents.page(saved.getDocumentPage(), ManagementEvent.Action.UPDATED);
        managementEvents.media(saved.getMediaAsset(), ManagementEvent.Action.UPDATED);
        return mapper.toDetails(saved);
    }

    @Transactional
    public DocumentPageFigureDetails review(
            Long pageId,
            Long figureId,
            String version,
            FigureReviewCommand command,
            FigureReviewState decision,
            String reviewer
    ) {
        if (decision != FigureReviewState.APPROVED
                && decision != FigureReviewState.REJECTED)
            throw new IllegalArgumentException("Figure review decision is invalid");

        if (command == null || command.reason() == null || command.reason().isBlank())
            throw new IllegalArgumentException("Figure review reason is required");

        if (reviewer == null || reviewer.isBlank())
            throw new IllegalArgumentException("Figure reviewer is required");

        DocumentPageFigure figure = requireFigure(pageId, figureId);
        requireMatch(figure.getRowVersion(), version);

        if (figure.getReviewState() == FigureReviewState.OUTDATED)
            throw new DocumentDependencyConflictException(
                    "Outdated figure extraction cannot be reviewed"
            );

        if (decision == FigureReviewState.APPROVED)
            validatePublicationMetadata(figure);

        figure.setReviewState(decision);
        figure.setReviewedBy(reviewer.trim());
        figure.setReviewedAt(LocalDateTime.now(ZoneOffset.UTC));
        figure.setReviewReason(command.reason().trim());
        DocumentPageFigure saved = figureRepository.saveAndFlush(figure);
        managementEvents.page(saved.getDocumentPage(), ManagementEvent.Action.STATUS_CHANGED);
        managementEvents.media(saved.getMediaAsset(), ManagementEvent.Action.STATUS_CHANGED);
        return mapper.toDetails(saved);
    }

    @Transactional
    public void delete(
            Long pageId,
            Long figureId,
            String version
    ) {
        DocumentPageFigure figure = requireFigure(pageId, figureId);
        requireMatch(figure.getRowVersion(), version);
        MediaAsset media = figure.getMediaAsset();
        List<Path> content = managedPaths(media);

        figureRepository.delete(figure);
        figureRepository.flush();

        if (mediaUsageChecker.isInUse(media.getId()))
            throw new DocumentDependencyConflictException(
                    "Figure media is referenced outside this figure"
            );

        mediaAssetRepository.delete(media);
        mediaAssetRepository.flush();
        fileCompensation.registerCommitCleanup(content);
        managementEvents.media(media, ManagementEvent.Action.DELETED);
        managementEvents.page(figure.getDocumentPage(), ManagementEvent.Action.UPDATED);
    }

    @Transactional
    public DocumentProcessingJobDetails reextract(Long pageId) {
        DocumentPage page = requirePage(pageId);
        DocumentPageOcrResult result = ocrResultRepository
                .findByDocumentPage_IdAndCurrentTrue(pageId)
                .orElseThrow(() -> new DocumentDependencyConflictException(
                        "Page has no current OCR result"
                ));

        if (candidateRepository.countByDocumentPageOcrResult_Id(result.getId()) == 0)
            throw new DocumentDependencyConflictException(
                    "Current OCR result has no figure candidates"
            );

        if (result.getDocumentPageMedia() == null
                || result.getDocumentPageMedia().getMediaAsset() == null)
            throw new DocumentDependencyConflictException(
                    "Current OCR result has no page rendition"
            );

        DocumentProcessingJob job = jobScheduler.queueFigureExtraction(
                page,
                result.getDocumentPageMedia().getMediaAsset(),
                result
        );
        result.setFigureExtractionState(FigureExtractionState.PENDING);
        result.setFigureExtractionMessage(null);
        ocrResultRepository.save(result);
        return historyMapper.toDetails(job);
    }

    private DocumentPage requirePage(Long pageId) {
        requireId(pageId, "Document page");
        return pageRepository.findById(pageId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Document page",
                        pageId
                ));
    }

    private DocumentPageFigure requireFigure(Long pageId, Long figureId) {
        requireId(pageId, "Document page");
        requireId(figureId, "Document page figure");
        return figureRepository.findByIdAndDocumentPage_Id(figureId, pageId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Document page figure",
                        figureId
                ));
    }

    private SourceReference reference(Long id) {
        if (id == null)
            return null;

        requireId(id, "Source reference");
        return sourceReferenceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Source reference",
                        id
                ));
    }

    private List<Path> managedPaths(MediaAsset media) {
        return java.util.stream.Stream.of(media.getFilePath(), media.getThumbnailPath())
                .filter(value -> !isBlank(value))
                .map(this::resolve)
                .toList();
    }

    private Path resolve(String storageKey) {
        try {
            return paths.resolveExisting(storageKey);
        } catch (IOException ex) {
            throw new IllegalStateException("Could not resolve figure media", ex);
        }
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void validatePublicationMetadata(DocumentPageFigure figure) {
        String caption = isBlank(figure.getCorrectedCaptionText())
                ? figure.getRawCaptionText()
                : figure.getCorrectedCaptionText();

        if (isBlank(caption))
            throw new IllegalArgumentException(
                    "A usable figure caption is required before approval"
            );

        if (figure.getSourceReference() == null)
            throw new IllegalArgumentException(
                    "A source reference is required before figure approval"
            );
    }

    private void resetReview(DocumentPageFigure figure) {
        figure.setReviewState(FigureReviewState.PENDING);
        figure.setReviewedBy(null);
        figure.setReviewedAt(null);
        figure.setReviewReason(null);
    }

    private Long sourceReferenceId(SourceReference sourceReference) {
        return sourceReference == null ? null : sourceReference.getId();
    }
}
