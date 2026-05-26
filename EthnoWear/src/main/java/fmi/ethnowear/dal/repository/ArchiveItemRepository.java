package fmi.ethnowear.dal.repository;

import fmi.ethnowear.application.enums.ArchiveType;
import fmi.ethnowear.application.enums.TrustedLevel;
import fmi.ethnowear.dal.entity.ArchiveItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ArchiveItemRepository extends JpaRepository<ArchiveItem, Long> {

    List<ArchiveItem> findByArchiveType(ArchiveType archiveType);

    List<ArchiveItem> findByTrustedLevel(TrustedLevel trustedLevel);

    List<ArchiveItem> findByOntologyRegionalEmbroideryLocalName(String ontologyRegionalEmbroideryLocalName);

    List<ArchiveItem> findByTitleBgContainingIgnoreCaseOrTitleEnContainingIgnoreCase(String titleBg, String titleEn);
}
