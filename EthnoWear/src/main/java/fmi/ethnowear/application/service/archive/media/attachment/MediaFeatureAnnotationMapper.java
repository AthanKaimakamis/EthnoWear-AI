package fmi.ethnowear.application.service.archive.media.attachment;

import fmi.ethnowear.application.dto.archive.media.MediaFeatureAnnotationDetails;
import fmi.ethnowear.application.dto.archive.media.MediaFeatureAnnotationWriteDto;
import fmi.ethnowear.persistence.jpa.entity.ArchiveItemFeature;
import fmi.ethnowear.persistence.jpa.entity.ArchiveItemMedia;
import fmi.ethnowear.persistence.jpa.entity.MediaFeatureAnnotation;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

@Component
public class MediaFeatureAnnotationMapper {

    public void apply(
            @NonNull MediaFeatureAnnotation annotation,
            @NonNull MediaFeatureAnnotationWriteDto input,
            ArchiveItemMedia itemMedia,
            ArchiveItemFeature itemFeature
    ) {
        annotation.setArchiveItemMedia(itemMedia);
        annotation.setArchiveItemFeature(itemFeature);
        annotation.setAnnotationType(input.annotationType());
        annotation.setX(input.x());
        annotation.setY(input.y());
        annotation.setWidth(input.width());
        annotation.setHeight(input.height());
        annotation.setNote(input.note());
    }

    public MediaFeatureAnnotationDetails toDetails(@NonNull MediaFeatureAnnotation annotation) {
        return new MediaFeatureAnnotationDetails(
                annotation.getId(), annotation.getArchiveItemMedia().getId(),
                annotation.getArchiveItemFeature().getId(), annotation.getAnnotationType(),
                annotation.getX(), annotation.getY(), annotation.getWidth(),
                annotation.getHeight(), annotation.getNote(), annotation.getCreatedAt(),
                annotation.getUpdatedAt()
        );
    }
}
