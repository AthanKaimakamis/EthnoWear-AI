package fmi.ethnowear.persistence.jpa.repository;

import fmi.ethnowear.persistence.jpa.entity.SourceReference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SourceReferenceRepository extends JpaRepository<SourceReference, Long> {

    List<SourceReference> findBySource_Id(Long sourceId);

    List<SourceReference> findBySource_IdAndChapter(Long sourceId, String chapter);

    boolean existsBySource_Id(Long sourceId);
}
