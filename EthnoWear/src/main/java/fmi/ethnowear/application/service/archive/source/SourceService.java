package fmi.ethnowear.application.service.archive.source;

import fmi.ethnowear.application.dto.archive.source.SourceDetails;
import fmi.ethnowear.application.dto.archive.source.SourceWriteDto;
import fmi.ethnowear.application.exception.ResourceInUseException;
import fmi.ethnowear.application.exception.ResourceNotFoundException;
import fmi.ethnowear.application.service.CrudService;
import fmi.ethnowear.persistence.jpa.entity.Source;
import fmi.ethnowear.persistence.jpa.repository.SourceRepository;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static fmi.ethnowear.util.TextUtils.isBlank;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SourceService implements CrudService<SourceWriteDto, SourceDetails> {

    private final SourceRepository sourceRepository;
    private final SourceMapper sourceMapper;
    private final SourceUsageChecker usageChecker;

    public Page<SourceDetails> findAll(Pageable pageable){
        return sourceRepository.findAll(pageable)
                .map(sourceMapper::toDetails);
    }

    public SourceDetails findById(Long id) {
        return sourceMapper.toDetails(requireSource(id));
    }

    @Transactional
    public SourceDetails create(SourceWriteDto input) {
        validate(input);

        Source source = new Source();
        sourceMapper.apply(source, input);

        return sourceMapper.toDetails(sourceRepository.save(source));
    }

    @Transactional
    public SourceDetails update(Long id, SourceWriteDto input) {
        validate(input);

        Source source = requireSource(id);
        sourceMapper.apply(source, input);

        return sourceMapper.toDetails(sourceRepository.save(source));
    }

    @Transactional
    public void delete(Long id) {
        Source source = requireSource(id);

        if (usageChecker.isInUse(id)) {
            throw new ResourceInUseException("Source", id);
        }

        sourceRepository.delete(source);
    }

    private @NonNull Source requireSource(Long id) {
        return sourceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Source", id));
    }

    private void validate(SourceWriteDto input) {
        if(input == null || isBlank(input.title()))
            throw new IllegalArgumentException("Source title is required");

        if(input.sourceType() == null)
            throw new IllegalArgumentException("Source type is required");
    }

}
