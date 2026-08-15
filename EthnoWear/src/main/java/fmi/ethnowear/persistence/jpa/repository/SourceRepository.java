package fmi.ethnowear.persistence.jpa.repository;

import fmi.ethnowear.domain.model.archive.SourceType;
import fmi.ethnowear.persistence.jpa.entity.Source;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SourceRepository extends JpaRepository<Source, Long> {

    /**
     * Finds every source marked as trusted.
     *
     * @return trusted sources, or an empty list when none exist
     */
    List<Source> findByTrustedTrue();

    /**
     * Finds sources belonging to the specified source type.
     *
     * @param sourceType source category used to filter the records
     * @return matching sources, or an empty list when none exist
     */
    List<Source> findBySourceType(SourceType sourceType);

    /**
     * Searches source titles without case sensitivity.
     *
     * @param title text searched within source titles
     * @return matching sources, or an empty list when none exist
     */
    List<Source> findByTitleContainingIgnoreCase(String title);
}
