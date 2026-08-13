package fmi.ethnowear.application.service.archive.media;

import fmi.ethnowear.api.dto.archive.media.ArchiveItemMediaDetails;
import fmi.ethnowear.api.dto.archive.media.ArchiveItemMediaWriteDto;
import fmi.ethnowear.dal.entity.ArchiveItem;
import fmi.ethnowear.dal.entity.ArchiveItemMedia;
import fmi.ethnowear.dal.entity.MediaAsset;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

@Component
public class ArchiveItemMediaMapper {

    public void apply(@NonNull ArchiveItemMedia itemMedia, @NonNull ArchiveItemMediaWriteDto input, ArchiveItem archiveItem, MediaAsset mediaAsset) {
        itemMedia.setArchiveItem(archiveItem);
        itemMedia.setMediaAsset(mediaAsset);
        itemMedia.setRole(input.role());
        itemMedia.setCaptionBg(input.captionBg());
        itemMedia.setCaptionEn(input.captionEn());
    }

    public ArchiveItemMediaDetails toDetails(@NonNull ArchiveItemMedia itemMedia) {
        return new ArchiveItemMediaDetails(
                itemMedia.getId(),
                itemMedia.getArchiveItem().getId(),
                itemMedia.getMediaAsset().getId(),
                itemMedia.getRole(),
                itemMedia.getCaptionBg(),
                itemMedia.getCaptionEn(),
                itemMedia.getCreatedAt(),
                itemMedia.getUpdatedAt()
        );
    }
}
