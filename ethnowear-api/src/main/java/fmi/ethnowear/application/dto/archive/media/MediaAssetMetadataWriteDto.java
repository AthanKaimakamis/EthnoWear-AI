package fmi.ethnowear.application.dto.archive.media;

import fmi.ethnowear.domain.model.rights.RightsStatus;
import jakarta.validation.constraints.Size;

public record MediaAssetMetadataWriteDto(
        Long sourceReferenceId,

        @Size(max = 2000)
        String description,

        RightsStatus rightsStatus,

        @Size(max = 500)
        String license,

        boolean publicDisplayAllowed
) {

    public MediaAssetMetadataWriteDto(
            Long sourceReferenceId,
            String description
    ) {
        this(
                sourceReferenceId,
                description,
                RightsStatus.UNKNOWN,
                null,
                false
        );
    }
}
