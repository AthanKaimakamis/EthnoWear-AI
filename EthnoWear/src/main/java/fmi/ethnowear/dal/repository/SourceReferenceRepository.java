package fmi.ethnowear.dal.repository;

import fmi.ethnowear.dal.entity.SourceReference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SourceReferenceRepository extends JpaRepository<SourceReference, Long> {

    /**
     * Finds all exact references belonging to one source.
     *
     * @param sourceId database identifier of the source
     * @return references belonging to the source, or an empty list when none exist
     */
    List<SourceReference> findBySource_Id(Long sourceId);

    /**
     * Finds references belonging to one source and chapter.
     *
     * @param sourceId database identifier of the source
     * @param chapter chapter used to filter the references
     * @return matching references, or an empty list when none exist
     */
    List<SourceReference> findBySource_IdAndChapter(Long sourceId, String chapter);

    /**
     * Checks whether a source has at least one exact reference.
     *
     * @param sourceId database identifier of the source
     * @return {@code true} when at least one reference exists for the source
     */
    boolean existsBySource_Id(Long sourceId);
}
