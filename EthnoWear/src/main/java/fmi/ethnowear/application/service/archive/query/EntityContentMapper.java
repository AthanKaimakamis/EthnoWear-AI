package fmi.ethnowear.application.service.archive.query;

import fmi.ethnowear.api.dto.archive.query.EntityContentDetails;
import fmi.ethnowear.api.dto.archive.query.EntityKnowledgeChunkDetails;
import fmi.ethnowear.api.dto.archive.query.EntityMediaAnnotationDetails;
import fmi.ethnowear.api.dto.archive.query.EntitySourceCitationDetails;
import fmi.ethnowear.application.enums.FeatureType;
import fmi.ethnowear.dal.entity.*;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class EntityContentMapper {

    public EntityContentDetails toDetails(
            FeatureType entityType,
            String ontologyIri,
            @NonNull List<KnowledgeChunk> chunks,
            List<MediaFeatureAnnotation> annotations,
            List<SourceReference> references
    ) {
        return new EntityContentDetails(
                entityType,
                ontologyIri,
                chunks.stream().map(this::toChunkDetails).toList(),
                annotations.stream().map(this::toMediaDetails).toList(),
                references.stream().map(this::toSourceDetails).toList()
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

    private EntitySourceCitationDetails toSourceDetails(SourceReference reference) {
        Source source = reference.getSource();

        return new EntitySourceCitationDetails(
                reference.getId(),
                source.getId(),
                source.getTitle(),
                source.getAuthor(),
                source.getPublisher(),
                source.getYear(),
                source.getSourceType(),
                source.getLanguage(),
                source.getFilePath(),
                source.getUrl(),
                source.getIsbn(),
                source.isTrusted(),
                reference.getChapter(),
                reference.getPageFrom(),
                reference.getPageTo(),
                reference.getFigureNumber(),
                reference.getSectionTitle(),
                reference.getCatalogNumber(),
                reference.getReferenceUrl(),
                reference.getAccessedDate(),
                reference.getLocator(),
                reference.getNote()
        );
    }
}
