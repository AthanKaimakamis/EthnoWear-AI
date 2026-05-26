package fmi.ethnowear.dal.repository;

import fmi.ethnowear.application.enums.MediaType;
import fmi.ethnowear.dal.entity.MediaAsset;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MediaAssetRepository extends JpaRepository<MediaAsset, Long> {

    List<MediaAsset> findByMediaType(MediaType mediaType);

    List<MediaAsset> findByChecksum(String checksum);

    List<MediaAsset> findBySourceReferenceId(Long sourceReferenceId);
}
