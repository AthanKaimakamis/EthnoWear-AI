package fmi.ethnowear.application.service.archive.query;

import fmi.ethnowear.application.dto.archive.query.EntityContentDetails;
import fmi.ethnowear.application.dto.archive.query.EntityKnowledgeChunkDetails;
import fmi.ethnowear.application.dto.archive.query.EntityMediaAnnotationDetails;
import fmi.ethnowear.domain.model.ontology.FeatureType;
import fmi.ethnowear.persistence.jpa.entity.KnowledgeChunk;
import fmi.ethnowear.persistence.jpa.entity.MediaAsset;
import fmi.ethnowear.persistence.jpa.entity.MediaFeatureAnnotation;
import fmi.ethnowear.persistence.jpa.entity.SourceReference;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class EntityContentMapper {

    private final EntitySourceCitationMapper sourceCitationMapper;

    public EntityContentDetails toDetails(
            FeatureType entityType,
            String ontologyIri,
            @NonNull List<KnowledgeChunk> chunks,
            @NonNull List<MediaFeatureAnnotation> annotations,
            @NonNull List<SourceReference> references
    ) {
        return new EntityContentDetails(
                entityType,
                ontologyIri,
                chunks.stream().map(this::toChunkDetails).toList(),
                annotations.stream().map(this::toMediaDetails).toList(),
                references.stream().map(sourceCitationMapper::toDetails).toList()
        );
    }

    @Contract("_ -> new")
    private @NonNull EntityKnowledgeChunkDetails toChunkDetails(@NonNull KnowledgeChunk chunk) {
        Long referenceId = chunk.getSourceReference() == null
                ? null
                : chunk.getSourceReference().getId();

        return new EntityKnowledgeChunkDetails(
                chunk.getId(),
                chunk.getChunkType(),
                chunk.getLanguage(),
                chunk.getContent(),
                referenceId
        );
    }

    @Contract("_ -> new")
    private @NonNull EntityMediaAnnotationDetails toMediaDetails(@NonNull MediaFeatureAnnotation annotation) {
        MediaAsset asset = annotation.getArchiveItemMedia().getMediaAsset();

        return new EntityMediaAnnotationDetails(
                annotation.getId(),
                annotation.getArchiveItemMedia().getArchiveItem().getId(),
                annotation.getArchiveItemMedia().getId(),
                asset.getId(),
                annotation.getArchiveItemMedia().getRole(),
                asset.getMediaType(),
                asset.getFileName(),
                asset.getFilePath(),
                asset.getStorageUrl(),
                asset.getMimeType(),
                annotation.getArchiveItemMedia().getCaptionBg(),
                annotation.getArchiveItemMedia().getCaptionEn(),
                annotation.getAnnotationType(),
                annotation.getX(),
                annotation.getY(),
                annotation.getWidth(),
                annotation.getHeight(),
                annotation.getNote()
        );
    }
}
