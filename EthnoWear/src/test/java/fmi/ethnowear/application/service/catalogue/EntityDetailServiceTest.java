package fmi.ethnowear.application.service.catalogue;

import fmi.ethnowear.application.dto.archive.query.ArchiveEvidenceDetails;
import fmi.ethnowear.application.dto.archive.query.EntityContentDetails;
import fmi.ethnowear.application.dto.catalogue.EntityDetailDetails;
import fmi.ethnowear.application.dto.catalogue.EntityOntologyDetails;
import fmi.ethnowear.domain.model.ontology.FeatureType;
import fmi.ethnowear.application.service.archive.query.ArchiveEvidenceService;
import fmi.ethnowear.application.service.archive.query.EntityContentService;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EntityDetailServiceTest {

    @Test
    void rejectsMissingPageableBeforeReadingData() {
        EntityDetailService service = new EntityDetailService(null, null, null);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.findByLocalName(
                        FeatureType.REGION,
                        "SofiaRegion",
                        "bg",
                        null
                )
        );

        assertEquals("Evidence pageable is required", exception.getMessage());
    }

    @Test
    void composesContentAndEvidenceUsingResolvedOntologyIdentity() {
        String iri = "http://example.org/ontology#ChainStitch";
        Pageable pageable = PageRequest.of(0, 12);
        EntityOntologyDetails ontologyDetails = new EntityOntologyDetails(
                FeatureType.TECHNIQUE,
                iri,
                "ChainStitch",
                "chain stitch",
                List.of(),
                null,
                "en",
                List.of(),
                Map.of()
        );
        EntityContentDetails contentDetails = new EntityContentDetails(
                FeatureType.TECHNIQUE,
                iri,
                List.of(),
                List.of(),
                List.of()
        );
        Page<ArchiveEvidenceDetails> evidence = Page.empty(pageable);

        OntologyEntityDetailReader ontologyReader = new OntologyEntityDetailReader(null, null, null) {
            @Override
            public EntityOntologyDetails find(FeatureType entityType, String localName, String languageTag) {
                assertEquals(FeatureType.TECHNIQUE, entityType);
                assertEquals("ChainStitch", localName);
                assertEquals("en", languageTag);
                return ontologyDetails;
            }
        };
        EntityContentService contentService = new EntityContentService(null, null, null, null) {
            @Override
            public EntityContentDetails findByOntologyEntity(
                    FeatureType entityType,
                    String ontologyIri,
                    String language
            ) {
                assertEquals(FeatureType.TECHNIQUE, entityType);
                assertEquals(iri, ontologyIri);
                assertEquals("en", language);
                return contentDetails;
            }
        };
        ArchiveEvidenceService evidenceService = new ArchiveEvidenceService(null, null, null, null) {
            @Override
            public Page<ArchiveEvidenceDetails> findByOntologyEntity(
                    FeatureType entityType,
                    String ontologyIri,
                    Pageable requestedPageable
            ) {
                assertEquals(FeatureType.TECHNIQUE, entityType);
                assertEquals(iri, ontologyIri);
                assertSame(pageable, requestedPageable);
                return evidence;
            }
        };
        EntityDetailService service = new EntityDetailService(
                ontologyReader,
                contentService,
                evidenceService
        );

        EntityDetailDetails details = service.findByLocalName(
                FeatureType.TECHNIQUE,
                "ChainStitch",
                "en",
                pageable
        );

        assertSame(ontologyDetails, details.ontology());
        assertSame(contentDetails, details.content());
        assertSame(evidence, details.evidence());
    }
}
