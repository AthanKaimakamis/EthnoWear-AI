package fmi.ethnowear.dal.repository;

import fmi.ethnowear.application.enums.MediaFeatureAnnotationType;
import fmi.ethnowear.dal.entity.MediaFeatureAnnotation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MediaFeatureAnnotationRepository extends JpaRepository<MediaFeatureAnnotation, Long> {

    List<MediaFeatureAnnotation> findByArchiveItemMediaId(Long archiveItemMediaId);

    List<MediaFeatureAnnotation> findByArchiveItemFeatureId(Long archiveItemFeatureId);

    List<MediaFeatureAnnotation> findByAnnotationType(MediaFeatureAnnotationType annotationType);
}
