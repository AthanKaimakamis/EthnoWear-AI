package fmi.ethnowear.application.service.catalogue;

import fmi.ethnowear.application.dto.catalogue.CategoryLinkDetails;
import fmi.ethnowear.application.dto.catalogue.EntityLinkDetails;
import fmi.ethnowear.application.service.catalogue.mapper.OntologyReferenceMapper;
import fmi.ethnowear.domain.model.ontology.FeatureType;
import fmi.ethnowear.domain.model.ontology.LocalizedOntologyResource;
import fmi.ethnowear.domain.model.ontology.OntologyLanguage;
import fmi.ethnowear.domain.model.ontology.OntologyResource;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OntologyReferenceMapperTest {

    private final OntologyReferenceMapper mapper = new OntologyReferenceMapper();

    @Test
    void createsSortedTypedEntityLinksWithLocalizedLabels() {
        List<EntityLinkDetails> result = mapper.toEntityLinks(
                FeatureType.TECHNIQUE,
                List.of(
                        resource("ZigzagStitch", "Zigzag stitch"),
                        resource("BackStitch", "Back stitch")
                ),
                List.of(localized("ZigzagStitch", "Зигзагообразен бод"))
        );

        assertEquals(List.of("BackStitch", "ZigzagStitch"),
                result.stream().map(EntityLinkDetails::localName).toList());
        assertEquals(FeatureType.TECHNIQUE, result.get(1).entityType());
        assertEquals("Зигзагообразен бод", result.get(1).label());
    }

    @Test
    void createsCategoryLinksForTheirTargetCatalogue() {
        List<CategoryLinkDetails> result = mapper.toCategoryLinks(
                FeatureType.ORNAMENT,
                List.of(resource("PlantOrnament", "Plant ornament")),
                List.of()
        );

        assertEquals(FeatureType.ORNAMENT, result.getFirst().targetEntityType());
        assertEquals("PlantOrnament", result.getFirst().localName());
        assertEquals("Plant ornament", result.getFirst().label());
    }

    private OntologyResource resource(String localName, String label) {
        return new OntologyResource("urn:test#" + localName, localName, label);
    }

    private LocalizedOntologyResource localized(String localName, String label) {
        return new LocalizedOntologyResource(
                "urn:test#" + localName,
                localName,
                label,
                List.of(),
                null,
                OntologyLanguage.BG
        );
    }
}
