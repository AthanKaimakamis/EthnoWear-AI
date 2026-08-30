package fmi.ethnowear.application.dto.archive.media;

import fmi.ethnowear.domain.model.archive.MediaType;
import jakarta.validation.constraints.NotNull;

public record MediaUploadRequest(
        Long sourceReferenceId,
        @NotNull MediaType mediaType,
        String category,
        String description
) { }
