package fmi.ethnowear.persistence.jpa.repository;

import fmi.ethnowear.domain.model.archive.MediaType;
import fmi.ethnowear.persistence.jpa.entity.MediaAsset;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MediaAssetRepository extends JpaRepository<MediaAsset, Long> {

    /**
     * Finds media assets belonging to the specified media type.
     *
     * @param mediaType media category used to filter the assets
     * @return matching media assets, or an empty list when none exist
     */
    List<MediaAsset> findByMediaType(MediaType mediaType);

    /**
     * Finds media assets having the specified checksum.
     *
     * @param checksum checksum used to identify matching media content
     * @return matching media assets, or an empty list when none exist
     */
    List<MediaAsset> findByChecksum(String checksum);

    /**
     * Finds media assets supported by an exact source reference.
     *
     * @param sourceReferenceId database identifier of the source reference
     * @return matching media assets, or an empty list when none exist
     */
    List<MediaAsset> findBySourceReferenceId(Long sourceReferenceId);

    /**
     * Checks whether an exact source reference supports at least one media asset.
     *
     * @param sourceReferenceId database identifier of the source reference
     * @return {@code true} when at least one media asset uses the reference
     */
    boolean existsBySourceReference_Id(Long sourceReferenceId);
}
