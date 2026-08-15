package fmi.ethnowear.application.service.archive.item;

import fmi.ethnowear.application.dto.archive.item.ArchiveItemFeatureDetails;
import fmi.ethnowear.application.dto.archive.item.ArchiveItemFeatureWriteDto;
import fmi.ethnowear.domain.model.ontology.FeatureType;
import fmi.ethnowear.application.exception.ResourceInUseException;
import fmi.ethnowear.application.exception.ResourceNotFoundException;
import fmi.ethnowear.application.service.CrudService;
import fmi.ethnowear.persistence.jpa.entity.ArchiveItem;
import fmi.ethnowear.persistence.jpa.entity.ArchiveItemFeature;
import fmi.ethnowear.persistence.jpa.entity.SourceReference;
import fmi.ethnowear.persistence.jpa.repository.ArchiveItemFeatureRepository;
import fmi.ethnowear.persistence.jpa.repository.ArchiveItemRepository;
import fmi.ethnowear.persistence.jpa.repository.SourceReferenceRepository;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Set;

import static fmi.ethnowear.util.TextUtils.isBlank;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ArchiveItemFeatureService implements CrudService<ArchiveItemFeatureWriteDto, ArchiveItemFeatureDetails> {

    private final ArchiveItemFeatureRepository featureRepository;
    private final ArchiveItemRepository archiveItemRepository;
    private final SourceReferenceRepository sourceReferenceRepository;
    private final ArchiveItemFeatureMapper featureMapper;
    private final ArchiveItemFeatureUsageChecker usageChecker;

    private static final Set<FeatureType> ALLOWED_FEATURE_TYPES = Set.of(
            FeatureType.ORNAMENT,
            FeatureType.COLOR,
            FeatureType.TECHNIQUE,
            FeatureType.MOTIF
    );

    @Override
    public Page<ArchiveItemFeatureDetails> findAll(Pageable pageable) {
        return featureRepository.findAll(pageable)
                .map(featureMapper::toDetails);
    }

    @Override
    public ArchiveItemFeatureDetails findById(Long id) {
        return featureMapper.toDetails(requireFeature(id));
    }

    @Override
    @Transactional
    public ArchiveItemFeatureDetails create(ArchiveItemFeatureWriteDto input) {
        validate(input);

        ArchiveItemFeature feature = new ArchiveItemFeature();
        apply(feature, input);

        return featureMapper.toDetails(featureRepository.save(feature));
    }

    @Override
    @Transactional
    public ArchiveItemFeatureDetails update(Long id, ArchiveItemFeatureWriteDto input) {
        validate(input);

        ArchiveItemFeature feature = requireFeature(id);
        apply(feature, input);

        return featureMapper.toDetails(featureRepository.save(feature));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        ArchiveItemFeature feature = requireFeature(id);

        if (usageChecker.isInUse(id))
            throw new ResourceInUseException("Archive item feature", id);

        featureRepository.delete(feature);
    }

    private void apply(ArchiveItemFeature feature, @NonNull ArchiveItemFeatureWriteDto input) {
        ArchiveItem item = archiveItemRepository
                .findById(input.archiveItemId())
                .orElseThrow(() -> new ResourceNotFoundException("Archive item", input.archiveItemId()));

        SourceReference reference = input.sourceReferenceId() == null
                ? null
                : sourceReferenceRepository
                .findById(input.sourceReferenceId())
                .orElseThrow(() -> new ResourceNotFoundException("Source reference", input.sourceReferenceId()));

        featureMapper.apply(feature, input, item, reference);
    }

    private @NonNull ArchiveItemFeature requireFeature(Long id) {
        return featureRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Archive item feature", id));
    }

    private void validate(ArchiveItemFeatureWriteDto input) {
        if (input == null || input.archiveItemId() == null)
            throw new IllegalArgumentException("Archive item is required");

        if (input.featureType() == null)
            throw new IllegalArgumentException("Feature type is required");

        if (!ALLOWED_FEATURE_TYPES.contains(input.featureType()))
            throw new IllegalArgumentException("Region and regional embroidery must be assigned directly to the archive item");

        if (isBlank(input.ontologyIri()))
            throw new IllegalArgumentException("Ontology IRI is required");

        if (isBlank(input.ontologyLocalName()))
            throw new IllegalArgumentException("Ontology local name is required");

        if (input.confidence() != null
                && (input.confidence().compareTo(BigDecimal.ZERO) < 0
                || input.confidence().compareTo(BigDecimal.ONE) > 0))
            throw new IllegalArgumentException("Confidence must be between 0 and 1");
    }
}
