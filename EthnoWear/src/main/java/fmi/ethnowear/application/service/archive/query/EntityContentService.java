package fmi.ethnowear.application.service.archive.query;

import fmi.ethnowear.api.dto.archive.query.EntityContentDetails;
import fmi.ethnowear.application.enums.FeatureType;
import fmi.ethnowear.dal.entity.ArchiveItemFeature;
import fmi.ethnowear.dal.entity.KnowledgeChunk;
import fmi.ethnowear.dal.entity.MediaFeatureAnnotation;
import fmi.ethnowear.dal.entity.SourceReference;
import fmi.ethnowear.dal.repository.ArchiveItemFeatureRepository;
import fmi.ethnowear.dal.repository.KnowledgeChunkRepository;
import fmi.ethnowear.dal.repository.MediaFeatureAnnotationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

import static fmi.ethnowear.util.TextUtils.isBlank;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EntityContentService {

    private final KnowledgeChunkRepository chunkRepository;
    private final ArchiveItemFeatureRepository featureRepository;
    private final MediaFeatureAnnotationRepository annotationRepository;
    private final EntityContentMapper contentMapper;

    public EntityContentDetails findByOntologyEntity(FeatureType entityType, String ontologyIri, String language){
        validate(entityType, ontologyIri, language);

        List<KnowledgeChunk> chunks = chunkRepository
                .findByOntologyIriAndLanguageOrderByIdAsc(ontologyIri, language);

        List<ArchiveItemFeature> features = featureRepository
                .findByFeatureTypeAndOntologyIriAndValidatedTrue(entityType, ontologyIri);

        List<MediaFeatureAnnotation> annotations = annotationRepository
                .findByArchiveItemFeature_FeatureTypeAndArchiveItemFeature_OntologyIriAndArchiveItemFeature_ValidatedTrueOrderByIdAsc(entityType, ontologyIri);

        List<SourceReference> references = collectReferences(chunks, features);

        return contentMapper.toDetails(entityType, ontologyIri, chunks, annotations, references);
    }


    private List<SourceReference> collectReferences(List<KnowledgeChunk> chunks, List<ArchiveItemFeature> features) {
        Map<Long, SourceReference> references = new LinkedHashMap<>();

        chunks.stream()
                .map(KnowledgeChunk::getSourceReference)
                .filter(Objects::nonNull)
                .forEach(reference -> references.put(reference.getId(), reference));

        features.stream()
                .map(ArchiveItemFeature::getSourceReference)
                .filter(Objects::nonNull)
                .forEach(reference -> references.put(reference.getId(), reference));

        return references.values()
                .stream()
                .sorted(Comparator.comparing(SourceReference::getId))
                .toList();
    }

    private void validate(FeatureType entityType, String ontologyIri, String language) {
        if(entityType == null)
            throw new IllegalArgumentException("Ontology entity type is required");

        if(isBlank(ontologyIri))
            throw new IllegalArgumentException("Ontology IRI is required");

        if(isBlank(language))
            throw new IllegalArgumentException("Language is required");
    }
}

