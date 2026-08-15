package fmi.ethnowear.api.dto.archive.query;

import fmi.ethnowear.api.dto.archive.media.ArchiveItemMediaDetails;
import fmi.ethnowear.api.dto.archive.media.MediaAssetDetails;
import fmi.ethnowear.api.dto.archive.media.MediaFeatureAnnotationDetails;

import java.util.List;

public record ArchiveItemMediaContentDetails(
        ArchiveItemMediaDetails media,
        MediaAssetDetails asset,
        List<MediaFeatureAnnotationDetails> annotations
) {
    public ArchiveItemMediaContentDetails {
        annotations = List.copyOf(annotations);
    }
}
