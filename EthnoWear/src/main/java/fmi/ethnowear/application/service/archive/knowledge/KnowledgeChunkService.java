package fmi.ethnowear.application.service.archive.knowledge;

import fmi.ethnowear.application.dto.archive.knowledge.KnowledgeChunkDetails;
import fmi.ethnowear.application.dto.archive.knowledge.KnowledgeChunkWriteDto;
import fmi.ethnowear.domain.model.archive.KnowledgeChunkType;
import fmi.ethnowear.domain.model.ontology.OntologyIdentity;
import fmi.ethnowear.application.exception.ResourceNotFoundException;
import fmi.ethnowear.application.service.CrudService;
import fmi.ethnowear.persistence.jpa.entity.KnowledgeChunk;
import fmi.ethnowear.persistence.jpa.entity.SourceReference;
import fmi.ethnowear.persistence.jpa.repository.KnowledgeChunkRepository;
import fmi.ethnowear.persistence.jpa.repository.SourceReferenceRepository;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static fmi.ethnowear.util.TextUtils.isBlank;
import static fmi.ethnowear.util.TextUtils.isNotBlank;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class KnowledgeChunkService implements CrudService<KnowledgeChunkWriteDto, KnowledgeChunkDetails> {

    private final KnowledgeChunkRepository chunkRepository;
    private final SourceReferenceRepository referenceRepository;
    private final KnowledgeChunkMapper chunkMapper;

    @Override
    public Page<KnowledgeChunkDetails> findAll(Pageable pageable) {
        return chunkRepository.findAll(pageable).map(chunkMapper::toDetails);
    }

    @Override
    public KnowledgeChunkDetails findById(Long id) {
        return chunkMapper.toDetails(requireChunk(id));
    }

    @Override
    @Transactional
    public KnowledgeChunkDetails create(KnowledgeChunkWriteDto input) {
        KnowledgeChunk chunk = new KnowledgeChunk();
        apply(chunk, input);
        return chunkMapper.toDetails(chunkRepository.save(chunk));
    }

    @Override
    @Transactional
    public KnowledgeChunkDetails update(Long id, KnowledgeChunkWriteDto input) {
        KnowledgeChunk chunk = requireChunk(id);
        apply(chunk, input);
        return chunkMapper.toDetails(chunkRepository.save(chunk));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        chunkRepository.delete(requireChunk(id));
    }

    private void apply(KnowledgeChunk chunk, KnowledgeChunkWriteDto input) {
        validate(input);

        SourceReference reference = input.sourceReferenceId() == null
                ? null
                : referenceRepository.findById(input.sourceReferenceId())
                    .orElseThrow(() -> new ResourceNotFoundException("Source reference", input.sourceReferenceId()));

        chunkMapper.apply(chunk, input, reference);
    }

    private @NonNull KnowledgeChunk requireChunk(Long id) {
        return chunkRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Knowledge chunk", id));
    }

    private void validate(KnowledgeChunkWriteDto input) {
        if(input == null)
            throw new IllegalArgumentException("Knowledge chunk input is required");

        if(input.chunkType() == null)
            throw new IllegalArgumentException("Knowledge chunk type is required");

        if(isBlank(input.language()))
            throw new IllegalArgumentException("Knowledge chunk language is required");

        if(input.language().length() > 10)
            throw new IllegalArgumentException("Knowledge chunk language cannot exceed 10 characters");

        if(isBlank(input.content()))
            throw new IllegalArgumentException("Knowledge chunk content is required");

        validateOntologyIdentity(input);
        validateEmbeddingIdentity(input);

        if(input.chunkType() == KnowledgeChunkType.SOURCE_EXCERPT && input.sourceReferenceId() == null)
            throw new IllegalArgumentException("Source excerpt requires a source reference");
    }

    private void validateOntologyIdentity(@NonNull KnowledgeChunkWriteDto input) {
        OntologyIdentity identity = new OntologyIdentity(
                input.ontologyIri(),
                input.ontologyLocalName()
        );

        if(identity.isIncomplete())
            throw new IllegalArgumentException("Ontology IRI and local name must be provided together");

        if(requiresOntologyIdentity(input.chunkType()) && !identity.isComplete())
            throw new IllegalArgumentException("Ontology identity is required for this knowledge chunk type");

        if(input.ontologyIri() != null && input.ontologyIri().length() > 1000)
            throw new IllegalArgumentException("Ontology IRI cannot exceed 1000 characters");

        if(input.ontologyLocalName() != null && input.ontologyLocalName().length() > 200)
            throw new IllegalArgumentException("Ontology local name cannot exceed 200 characters");
    }

    private void validateEmbeddingIdentity(@NonNull KnowledgeChunkWriteDto input) {
        boolean hasModel = isNotBlank(input.embeddingModel());
        boolean hasId = isNotBlank(input.embeddingId());

        if(hasModel != hasId)
            throw new IllegalArgumentException("Embedding model and ID must be provided together");

        if(input.embeddingModel() != null && input.embeddingModel().length() > 100)
            throw new IllegalArgumentException("Embedding model cannot exceed 100 characters");

        if(input.embeddingId() != null && input.embeddingId().length() > 255)
            throw new IllegalArgumentException("Embedding ID cannot exceed 255 characters");
    }

    private boolean requiresOntologyIdentity(KnowledgeChunkType chunkType) {
        return chunkType != KnowledgeChunkType.GENERAL
                && chunkType != KnowledgeChunkType.SOURCE_EXCERPT;
    }

}
