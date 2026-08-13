package fmi.ethnowear.api.dto.archive.media;

import fmi.ethnowear.application.enums.MediaFeatureAnnotationType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record MediaFeatureAnnotationWriteDto(
        @NotNull
        Long archiveItemMediaId,

        @NotNull
        Long archiveItemFeatureId,

        @NotNull
        MediaFeatureAnnotationType annotationType,

        @DecimalMin("0.0")
        @DecimalMax("1.0")
        @Digits(integer = 1, fraction = 6)
        BigDecimal x,

        @DecimalMin("0.0")
        @DecimalMax("1.0")
        @Digits(integer = 1, fraction = 6)
        BigDecimal y,

        @DecimalMin(value = "0.0", inclusive = false)
        @DecimalMax("1.0")
        @Digits(integer = 1, fraction = 6)
        BigDecimal width,

        @DecimalMin(value = "0.0", inclusive = false)
        @DecimalMax("1.0")
        @Digits(integer = 1, fraction = 6)
        BigDecimal height,

        String note
) {
}
