package fmi.ethnowear.persistence.jpa.repository;

import fmi.ethnowear.domain.model.archive.MediaRole;
import fmi.ethnowear.persistence.jpa.entity.ArchiveItemMedia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.Collection;
import java.util.List;

public interface ArchiveItemMediaRepository extends JpaRepository<ArchiveItemMedia, Long> {

    /**
     * Finds all media links belonging to one archive item.
     *
     * @param archiveItemId database identifier of the archive item
     * @return media links for the item, or an empty list when none exist
     */
    @EntityGraph(attributePaths = "mediaAsset")
    List<ArchiveItemMedia> findByArchiveItemId(Long archiveItemId);

    /**
     * Finds candidate preview media for multiple archive items and loads each media asset eagerly.
     *
     * @param archiveItemIds database identifiers of the archive items
     * @param roles presentation roles eligible for preview use
     * @return matching archive-item media links with their media assets
     */
    @EntityGraph(attributePaths = "mediaAsset")
    List<ArchiveItemMedia> findByArchiveItem_IdInAndRoleIn(
            Collection<Long> archiveItemIds,
            Collection<MediaRole> roles
    );

    /**
     * Finds media links for one archive item having the specified presentation role.
     *
     * @param archiveItemId database identifier of the archive item
     * @param role role used to filter the media links
     * @return matching media links, or an empty list when none exist
     */
    List<ArchiveItemMedia> findByArchiveItemIdAndRole(Long archiveItemId, MediaRole role);

    /**
     * Checks whether an archive item has at least one media link.
     *
     * @param archiveItemId database identifier of the archive item
     * @return {@code true} when at least one media link belongs to the item
     */
    boolean existsByArchiveItem_Id(Long archiveItemId);

    /**
     * Finds archive-item links using one media asset.
     *
     * @param mediaAssetId database identifier of the media asset
     * @return links using the media asset, or an empty list when none exist
     */
    List<ArchiveItemMedia> findByMediaAssetId(Long mediaAssetId);

    /**
     * Checks whether a media asset is linked to at least one archive item.
     *
     * @param mediaAssetId database identifier of the media asset
     * @return {@code true} when at least one archive-item link uses the asset
     */
    boolean existsByMediaAsset_Id(Long mediaAssetId);

    /**
     * Checks whether an archive item has media with the specified role.
     *
     * @param archiveItemId database identifier of the archive item
     * @param role required media role
     * @return {@code true} when matching media exists
     */
    boolean existsByArchiveItem_IdAndRole(Long archiveItemId, MediaRole role);
}
