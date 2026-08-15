package fmi.ethnowear.application.service.archive.query;

import fmi.ethnowear.application.dto.archive.query.ArchiveEvidenceDetails;
import fmi.ethnowear.application.dto.archive.query.ArchiveEvidenceFeatureDetails;
import fmi.ethnowear.application.dto.archive.query.ArchiveEvidenceMediaDetails;
import fmi.ethnowear.persistence.jpa.entity.ArchiveItem;
import fmi.ethnowear.persistence.jpa.entity.ArchiveItemFeature;
import fmi.ethnowear.persistence.jpa.entity.ArchiveItemMedia;
import fmi.ethnowear.persistence.jpa.entity.MediaAsset;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ArchiveEvidenceMapper {

    public ArchiveEvidenceDetails toDetails(@NonNull ArchiveItem item, boolean directlyLinked,
                                            @NonNull List<ArchiveItemFeature> matchingFeatures,
                                            ArchiveItemMedia previewMedia) {
        return new ArchiveEvidenceDetails(
                item.getId(),
                item.getArchiveType(),
                item.getTitleBg(),
                item.getTitleEn(),
                item.getPeriodText(),
                item.getOriginText(),
                item.getCurrentLocation(),
                item.getTrustedLevel(),
                directlyLinked,
                matchingFeatures.stream().map(this::toFeatureDetails).toList(),
                previewMedia == null ? null : toMediaDetails(previewMedia)
        );
    }

    @Contract("_ -> new")
    private @NonNull ArchiveEvidenceFeatureDetails toFeatureDetails(@NonNull ArchiveItemFeature feature) {
        Long referenceId = feature.getSourceReference() == null
                ? null
                : feature.getSourceReference().getId();

        return new ArchiveEvidenceFeatureDetails(
                feature.getId(),
                feature.getFeatureType(),
                feature.getOntologyIri(),
                feature.getOntologyLocalName(),
                feature.getConfidence(),
                feature.getNotes(),
                referenceId
        );
    }

    @Contract("_ -> new")
    private @NonNull ArchiveEvidenceMediaDetails toMediaDetails(@NonNull ArchiveItemMedia itemMedia) {
        MediaAsset asset = itemMedia.getMediaAsset();

        return new ArchiveEvidenceMediaDetails(
                itemMedia.getId(),
                asset.getId(),
                itemMedia.getRole(),
                asset.getFileName(),
                asset.getFilePath(),
                asset.getStorageUrl(),
                asset.getMimeType(),
                asset.getMediaType(),
                itemMedia.getCaptionBg(),
                itemMedia.getCaptionEn()
        );
    }
}
