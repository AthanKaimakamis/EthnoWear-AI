package fmi.ethnowear.application.service.archive.media;

import fmi.ethnowear.application.dto.archive.media.MediaFeatureAnnotationDetails;
import fmi.ethnowear.application.dto.archive.media.MediaFeatureAnnotationWriteDto;
import fmi.ethnowear.application.exception.ResourceNotFoundException;
import fmi.ethnowear.application.service.CrudService;
import fmi.ethnowear.persistence.jpa.entity.ArchiveItemFeature;
import fmi.ethnowear.persistence.jpa.entity.ArchiveItemMedia;
import fmi.ethnowear.persistence.jpa.entity.MediaFeatureAnnotation;
import fmi.ethnowear.persistence.jpa.repository.ArchiveItemFeatureRepository;
import fmi.ethnowear.persistence.jpa.repository.ArchiveItemMediaRepository;
import fmi.ethnowear.persistence.jpa.repository.MediaFeatureAnnotationRepository;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static fmi.ethnowear.util.IdentifierUtils.requireId;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MediaFeatureAnnotationService implements CrudService<MediaFeatureAnnotationWriteDto, MediaFeatureAnnotationDetails> {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal ONE = BigDecimal.ONE;

    private final MediaFeatureAnnotationRepository annotationRepository;
    private final ArchiveItemMediaRepository itemMediaRepository;
    private final ArchiveItemFeatureRepository itemFeatureRepository;
    private final MediaFeatureAnnotationMapper annotationMapper;

    @Override
    public Page<MediaFeatureAnnotationDetails> findAll(Pageable pageable) {
        return annotationRepository.findAll(pageable)
                .map(annotationMapper::toDetails);
    }

    @Override
    public MediaFeatureAnnotationDetails findById(Long id) {
        return annotationMapper.toDetails(requireAnnotation(id));
    }

    @Override
    @Transactional
    public MediaFeatureAnnotationDetails create(MediaFeatureAnnotationWriteDto input) {
        validate(input);

        MediaFeatureAnnotation annotation = new MediaFeatureAnnotation();
        apply(annotation, input);

        return annotationMapper.toDetails(annotationRepository.save(annotation));
    }

    @Override
    @Transactional
    public MediaFeatureAnnotationDetails update(Long id, MediaFeatureAnnotationWriteDto input) {
        validate(input);

        MediaFeatureAnnotation annotation = requireAnnotation(id);
        apply(annotation, input);

        return annotationMapper.toDetails(annotationRepository.save(annotation));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        annotationRepository.delete(requireAnnotation(id));
    }

    private void apply(MediaFeatureAnnotation annotation, @NonNull MediaFeatureAnnotationWriteDto input) {
        ArchiveItemMedia itemMedia = itemMediaRepository.findById(input.archiveItemMediaId())
                .orElseThrow(() -> new ResourceNotFoundException("Archive item media", input.archiveItemMediaId()));

        ArchiveItemFeature itemFeature = itemFeatureRepository.findById(input.archiveItemFeatureId())
                .orElseThrow(() -> new ResourceNotFoundException("Archive item feature", input.archiveItemFeatureId()));

        if (!itemMedia.getArchiveItem().getId().equals(itemFeature.getArchiveItem().getId()))
            throw new IllegalArgumentException("Media and feature must belong to the same archive item");

        annotationMapper.apply(annotation, input, itemMedia, itemFeature);
    }

    private @NonNull MediaFeatureAnnotation requireAnnotation(Long id) {
        requireId(id, "Media feature annotation");

        return annotationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Media feature annotation", id));
    }

    private void validate(MediaFeatureAnnotationWriteDto input) {
        if (input == null)
            throw new IllegalArgumentException("Media feature annotation input is required");

        requireId(input.archiveItemMediaId(), "Archive item media");
        requireId(input.archiveItemFeatureId(), "Archive item feature");

        if (input.annotationType() == null)
            throw new IllegalArgumentException("Annotation type is required");

        validateCoordinates(input);
    }

    private void validateCoordinates(@NonNull MediaFeatureAnnotationWriteDto input) {
        boolean hasCoordinates = input.x() != null || input.y() != null
                || input.width() != null || input.height() != null;

        if (!hasCoordinates)
            return;

        if (input.x() == null || input.y() == null || input.width() == null || input.height() == null)
            throw new IllegalArgumentException("Annotation coordinates must define a complete rectangle");

        if (isOutsideNormalizedRange(input.x()) || isOutsideNormalizedRange(input.y()))
            throw new IllegalArgumentException("Annotation position must be between 0 and 1");

        if (isOutsidePositiveNormalizedRange(input.width()) || isOutsidePositiveNormalizedRange(input.height()))
            throw new IllegalArgumentException("Annotation dimensions must be greater than 0 and at most 1");

        if (hasUnsupportedScale(input.x()) || hasUnsupportedScale(input.y())
                || hasUnsupportedScale(input.width()) || hasUnsupportedScale(input.height()))
            throw new IllegalArgumentException("Annotation coordinates cannot have more than 6 decimal places");

        if (input.x().add(input.width()).compareTo(ONE) > 0
                || input.y().add(input.height()).compareTo(ONE) > 0)
            throw new IllegalArgumentException("Annotation rectangle must fit within the media bounds");
    }

    private boolean isOutsideNormalizedRange(@NonNull BigDecimal value) {
        return value.compareTo(ZERO) < 0 || value.compareTo(ONE) > 0;
    }

    private boolean isOutsidePositiveNormalizedRange(@NonNull BigDecimal value) {
        return value.compareTo(ZERO) <= 0 || value.compareTo(ONE) > 0;
    }

    private boolean hasUnsupportedScale(@NonNull BigDecimal value) {
        return value.scale() > 6;
    }

}
