package fmi.ethnowear.persistence.jpa.repository;

import fmi.ethnowear.domain.model.archive.MediaRole;
import fmi.ethnowear.persistence.jpa.entity.ArchiveItemMedia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.Collection;
import java.util.List;

public interface ArchiveItemMediaRepository extends JpaRepository<ArchiveItemMedia, Long> {

    @EntityGraph(attributePaths = "mediaAsset")
    List<ArchiveItemMedia> findByArchiveItemId(Long archiveItemId);

    @EntityGraph(attributePaths = "mediaAsset")
    List<ArchiveItemMedia> findByArchiveItem_IdInAndRoleIn(
            Collection<Long> archiveItemIds,
            Collection<MediaRole> roles
    );

    List<ArchiveItemMedia> findByArchiveItemIdAndRole(
            Long archiveItemId,
            MediaRole role
    );

    boolean existsByArchiveItem_Id(Long archiveItemId);

    List<ArchiveItemMedia> findByMediaAssetId(Long mediaAssetId);

    boolean existsByMediaAsset_Id(Long mediaAssetId);

    boolean existsByArchiveItem_IdAndRole(Long archiveItemId, MediaRole role);
}
