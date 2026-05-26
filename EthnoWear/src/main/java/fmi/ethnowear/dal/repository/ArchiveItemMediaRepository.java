package fmi.ethnowear.dal.repository;

import fmi.ethnowear.application.enums.MediaRole;
import fmi.ethnowear.dal.entity.ArchiveItem;
import fmi.ethnowear.dal.entity.ArchiveItemMedia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ArchiveItemMediaRepository extends JpaRepository<ArchiveItemMedia, Long> {

    List<ArchiveItemMedia> findByArchiveItemId(Long archiveItemId);

    List<ArchiveItemMedia> findByArchiveItemIdAndRole(Long archiveItemId, MediaRole role);

    List<ArchiveItemMedia> findByMediaAssetId(Long mediaAssetId);
}
