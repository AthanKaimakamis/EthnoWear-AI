package fmi.ethnowear.persistence.jpa.repository;

import fmi.ethnowear.domain.model.archive.SourceType;
import fmi.ethnowear.persistence.jpa.entity.Source;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SourceRepository extends JpaRepository<Source, Long> {

    List<Source> findByTrustedTrue();

    List<Source> findBySourceType(SourceType sourceType);

    List<Source> findByTitleContainingIgnoreCase(String title);
}
