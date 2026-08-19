package fmi.ethnowear.application.service.archive.source;

import fmi.ethnowear.application.dto.archive.source.SourceReferenceDetails;
import fmi.ethnowear.application.dto.archive.source.SourceReferenceWriteDto;
import fmi.ethnowear.application.exception.ResourceInUseException;
import fmi.ethnowear.application.exception.ResourceNotFoundException;
import fmi.ethnowear.application.service.CrudService;
import fmi.ethnowear.persistence.jpa.entity.Source;
import fmi.ethnowear.persistence.jpa.entity.SourceReference;
import fmi.ethnowear.persistence.jpa.repository.SourceReferenceRepository;
import fmi.ethnowear.persistence.jpa.repository.SourceRepository;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static fmi.ethnowear.util.IdentifierUtils.requireId;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SourceReferenceService implements CrudService<SourceReferenceWriteDto, SourceReferenceDetails> {

    private final SourceReferenceRepository referenceRepository;
    private final SourceRepository sourceRepository;
    private final SourceReferenceMapper referenceMapper;
    private final SourceReferenceUsageChecker usageChecker;

    public Page<SourceReferenceDetails> findAll(Pageable pageable) {
        return referenceRepository.findAll(pageable).map(referenceMapper::toDetails);
    }

    public SourceReferenceDetails findById(Long id) {
        return referenceMapper.toDetails(requireReference(id));
    }

    @Transactional
    public SourceReferenceDetails create(SourceReferenceWriteDto input) {
        SourceReference reference = new SourceReference();
        apply(reference, input);
        return referenceMapper.toDetails(referenceRepository.save(reference));
    }

    @Transactional
    public SourceReferenceDetails update(Long id, SourceReferenceWriteDto input) {
        SourceReference reference = requireReference(id);
        apply(reference, input);
        return referenceMapper.toDetails(referenceRepository.save(reference));
    }

    @Transactional
    public void delete(Long id) {
        SourceReference reference = requireReference(id);

        if (usageChecker.isInUse(id)) {
            throw new ResourceInUseException("Source reference", id);
        }

        referenceRepository.delete(reference);
    }

    private void apply(SourceReference reference, SourceReferenceWriteDto input) {
        validate(input);

        Source source = sourceRepository.findById(input.sourceId())
                .orElseThrow(() -> new ResourceNotFoundException("Source", input.sourceId()));

        referenceMapper.apply(reference, input, source);
    }

    private void validate(SourceReferenceWriteDto input) {
        if(input == null)
            throw new IllegalArgumentException("Source is required");

        requireId(input.sourceId(), "Source");

        if(input.pageFrom() != null && input.pageFrom() < 1)
            throw new IllegalArgumentException("Page from must be positive");

        if(input.pageTo() != null && input.pageTo() < 1)
            throw new IllegalArgumentException("Page to must be positive");

        if(input.pageTo() != null && input.pageFrom() != null
            && input.pageFrom() > input.pageTo())
            throw new IllegalArgumentException("Page from cannot be greater than page to");
    }

    private @NonNull SourceReference requireReference(Long id) {
        requireId(id, "Source reference");

        return referenceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Source reference", id));
    }
}
