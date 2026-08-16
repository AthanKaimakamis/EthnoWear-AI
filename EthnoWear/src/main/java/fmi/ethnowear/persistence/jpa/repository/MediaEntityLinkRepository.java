package fmi.ethnowear.persistence.jpa.repository;

import fmi.ethnowear.persistence.jpa.entity.MediaEntityLink;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MediaEntityLinkRepository extends JpaRepository<MediaEntityLink, Long> {
    boolean existsByMediaAsset_Id(Long mediaAssetId);
}
