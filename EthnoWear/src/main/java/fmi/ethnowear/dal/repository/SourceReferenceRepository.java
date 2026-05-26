package fmi.ethnowear.dal.repository;

import fmi.ethnowear.dal.entity.SourceReference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SourceReferenceRepository extends JpaRepository<SourceReference, Long> {

    List<SourceReference> findBySourceId(Long sourceId);

    List<SourceReference> findBySourceIdAndChapter(Long sourceId, String chapter);
}
