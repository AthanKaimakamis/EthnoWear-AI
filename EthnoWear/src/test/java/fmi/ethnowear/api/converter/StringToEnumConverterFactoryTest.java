package fmi.ethnowear.api.converter;

import fmi.ethnowear.domain.model.ontology.FeatureType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.core.convert.converter.Converter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StringToEnumConverterFactoryTest {

    private final Converter<String, FeatureType> converter = new StringToEnumConverterFactory()
            .getConverter(FeatureType.class);

    @ParameterizedTest
    @CsvSource({
            "ornament, ORNAMENT",
            "ornaments, ORNAMENT",
            "colors, COLOR",
            "techniques, TECHNIQUE",
            "motifs, MOTIF",
            "regions, REGION",
            "regional-embroidery, REGIONAL_EMBROIDERY",
            "regional-embroideries, REGIONAL_EMBROIDERY",
            "regional_embroidery, REGIONAL_EMBROIDERY",
            "TECHNIQUE, TECHNIQUE"
    })
    void convertsNamesAndAliases(String source, FeatureType expected) {
        assertEquals(expected, converter.convert(source));
    }

    @Test
    void convertsAliasesWithoutCaseSensitivityAndTrimsWhitespace() {
        assertEquals(FeatureType.TECHNIQUE, converter.convert("  TeChNiQuEs  "));
    }

    @Test
    void rejectsBlankValue() {
        assertThrows(IllegalArgumentException.class, () -> converter.convert(" "));
    }

    @Test
    void rejectsUnsupportedValue() {
        assertThrows(IllegalArgumentException.class, () -> converter.convert("unknown"));
    }
}
