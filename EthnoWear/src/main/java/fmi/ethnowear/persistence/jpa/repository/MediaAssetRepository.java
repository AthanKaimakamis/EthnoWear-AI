package fmi.ethnowear.persistence.jpa.repository;

import fmi.ethnowear.domain.model.archive.MediaType;
import fmi.ethnowear.persistence.jpa.entity.MediaAsset;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MediaAssetRepository extends JpaRepository<MediaAsset, Long> {

    List<MediaAsset> findByMediaType(MediaType mediaType);

    List<MediaAsset> findByChecksum(String checksum);

    List<MediaAsset> findBySourceReferenceId(Long sourceReferenceId);

    boolean existsBySourceReference_Id(Long sourceReferenceId);
}
