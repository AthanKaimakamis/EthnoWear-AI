package fmi.ethnowear.application.exception;

import lombok.Getter;

@Getter
public class MediaContentPurgedException extends RuntimeException {

    private final Long mediaAssetId;

    public MediaContentPurgedException(Long mediaAssetId) {
        super("Media content was removed by the retention policy: " + mediaAssetId);
        this.mediaAssetId = mediaAssetId;
    }
}
