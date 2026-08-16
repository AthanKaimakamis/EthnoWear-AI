package fmi.ethnowear.application.service.archive.item;

import fmi.ethnowear.application.dto.archive.item.ArchiveItemDetails;
import fmi.ethnowear.application.dto.archive.item.ArchiveItemWriteDto;
import fmi.ethnowear.application.exception.ResourceInUseException;
import fmi.ethnowear.application.exception.ResourceNotFoundException;
import fmi.ethnowear.application.service.CrudService;
import fmi.ethnowear.application.service.archive.workflow.ArchiveItemWorkflowGuard;
import fmi.ethnowear.domain.model.archive.PublicationStatus;
import fmi.ethnowear.persistence.jpa.entity.ArchiveItem;
import fmi.ethnowear.persistence.jpa.entity.SourceReference;
import fmi.ethnowear.persistence.jpa.repository.ArchiveItemRepository;
import fmi.ethnowear.persistence.jpa.repository.SourceReferenceRepository;
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
public class ArchiveItemService implements CrudService<ArchiveItemWriteDto, ArchiveItemDetails> {

    private final ArchiveItemRepository archiveItemRepository;
    private final SourceReferenceRepository referenceRepository;
    private final ArchiveItemMapper archiveItemMapper;
    private final ArchiveItemUsageChecker usageChecker;
    private final ArchiveItemOntologyValidator ontologyValidator;
    private final ArchiveItemWorkflowGuard workflowGuard;

    @Override
    public Page<ArchiveItemDetails> findAll(Pageable pageable) {
        return archiveItemRepository.findAll(pageable)
                .map(archiveItemMapper::toDetails);
    }

    @Override
    public ArchiveItemDetails findById(Long id) {
        return archiveItemMapper.toDetails(requireItem(id));
    }

    @Override
    @Transactional
    public ArchiveItemDetails create(ArchiveItemWriteDto input) {
        validate(input);

        ArchiveItem item = new ArchiveItem();
        item.setPublicationStatus(PublicationStatus.DRAFT);
        apply(item, input);

        return archiveItemMapper.toDetails(archiveItemRepository.save(item));
    }

    @Override
    @Transactional
    public ArchiveItemDetails update(Long id, ArchiveItemWriteDto input) {
        validate(input);

        ArchiveItem item = requireItem(id);
        workflowGuard.requireDraft(item);
        apply(item, input);

        return archiveItemMapper.toDetails(archiveItemRepository.save(item));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        ArchiveItem item = requireItem(id);
        workflowGuard.requireDraft(item);

        if(usageChecker.isInUse(id))
            throw new ResourceInUseException("Archive item", id);

        archiveItemRepository.delete(item);
    }

    private void apply(ArchiveItem item, @NonNull ArchiveItemWriteDto input) {
        SourceReference reference = referenceRepository
                .findById(input.sourceReferenceId())
                .orElseThrow(() -> new ResourceNotFoundException("Source reference", input.sourceReferenceId()));

        archiveItemMapper.apply(item, input, reference);
    }

    private @NonNull ArchiveItem requireItem(Long id) {
        return  archiveItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Archive item", id));
    }

    private void validate(ArchiveItemWriteDto input) {
        if (input == null || input.sourceReferenceId() == null)
            throw new IllegalArgumentException("Source reference is required");

        if(isBlank(input.titleBg()) && isBlank(input.titleEn()))
            throw new IllegalArgumentException("At least one title is required");

        if(input.archiveType() == null)
            throw new IllegalArgumentException("Archive type is required");

        if(input.trustedLevel() == null)
            throw new IllegalArgumentException("Trusted level is required");

        ontologyValidator.validateClassifications(input);
    }

}
