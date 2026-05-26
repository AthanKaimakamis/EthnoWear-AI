package fmi.ethnowear.dal.repository;

import fmi.ethnowear.application.enums.SourceType;
import fmi.ethnowear.dal.entity.Source;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SourceRepository extends JpaRepository<Source, Long> {

    List<Source> findByTrustedTrue();

    List<Source> findBySourceType(SourceType sourceType);

    List<Source> findByTitleContainingIgnoreCase(String title);
}
