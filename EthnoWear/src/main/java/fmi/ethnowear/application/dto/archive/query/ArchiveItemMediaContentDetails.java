package fmi.ethnowear.application.dto.archive.query;

import fmi.ethnowear.application.dto.archive.media.ArchiveItemMediaDetails;
import fmi.ethnowear.application.dto.archive.media.MediaAssetDetails;
import fmi.ethnowear.application.dto.archive.media.MediaFeatureAnnotationDetails;

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
