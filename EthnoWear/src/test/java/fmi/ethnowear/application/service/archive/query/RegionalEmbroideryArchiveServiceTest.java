package fmi.ethnowear.application.service.archive.query;

import fmi.ethnowear.application.dto.archive.query.ArchiveEvidenceDetails;
import fmi.ethnowear.application.dto.archive.query.RegionalEmbroideryArchiveOverviewDetails;
import fmi.ethnowear.application.dto.catalogue.EntityOntologyDetails;
import fmi.ethnowear.domain.model.archive.ArchiveType;
import fmi.ethnowear.domain.model.ontology.FeatureType;
import fmi.ethnowear.domain.model.archive.TrustedLevel;
import fmi.ethnowear.application.service.catalogue.EntityCardMapper;
import fmi.ethnowear.application.service.catalogue.OntologyEntityDetailReader;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RegionalEmbroideryArchiveServiceTest {

    @Test
    void buildsSortedSectionsWithCountsAndBoundedPreviews() {
        StubOntologyReader ontologyReader = new StubOntologyReader(List.of(
                entity("ShoplukEmbroidery", "Shopluk embroidery"),
                entity("RhodopeEmbroidery", "Rhodope embroidery")
        ));
        StubArchiveEvidenceService evidenceService = new StubArchiveEvidenceService();
        RegionalEmbroideryArchiveService service = new RegionalEmbroideryArchiveService(
                ontologyReader,
                new EntityCardMapper(),
                evidenceService
        );

        RegionalEmbroideryArchiveOverviewDetails result = service.findOverview("en", 4);

        assertEquals("en", result.language());
        assertEquals("RhodopeEmbroidery", result.sections().getFirst().regionalEmbroidery().localName());
        assertEquals("ShoplukEmbroidery", result.sections().getLast().regionalEmbroidery().localName());
        assertEquals(3, result.sections().getLast().totalItems());
        assertEquals(3, result.sections().getLast().previewItems().size());
        assertEquals(4, evidenceService.pageables.getFirst().getPageSize());
        assertEquals("id: DESC", evidenceService.pageables.getFirst().getSort().toString());
    }

    @Test
    void rejectsPreviewSizeOutsideSupportedRange() {
        RegionalEmbroideryArchiveService service = new RegionalEmbroideryArchiveService(
                new StubOntologyReader(List.of()),
                new EntityCardMapper(),
                new StubArchiveEvidenceService()
        );

        assertThrows(IllegalArgumentException.class, () -> service.findOverview("bg", 0));
        assertThrows(IllegalArgumentException.class, () -> service.findOverview("bg", 13));
    }

    private EntityOntologyDetails entity(String localName, String label) {
        return new EntityOntologyDetails(
                FeatureType.REGIONAL_EMBROIDERY,
                "urn:ethnowear#" + localName,
                localName,
                label,
                List.of(),
                null,
                "en",
                List.of(),
                Map.of()
        );
    }

    private static final class StubOntologyReader extends OntologyEntityDetailReader {

        private final List<EntityOntologyDetails> entities;

        private StubOntologyReader(List<EntityOntologyDetails> entities) {
            super(null, null, null);
            this.entities = entities;
        }

        @Override
        public List<EntityOntologyDetails> list(FeatureType entityType, String languageTag) {
            assertEquals(FeatureType.REGIONAL_EMBROIDERY, entityType);
            return entities;
        }
    }

    private static final class StubArchiveEvidenceService extends ArchiveEvidenceService {

        private final List<Pageable> pageables = new ArrayList<>();

        private StubArchiveEvidenceService() {
            super(null, null, null, null);
        }

        @Override
        public Page<ArchiveEvidenceDetails> findByOntologyEntity(
                FeatureType entityType,
                String ontologyIri,
                Pageable pageable
        ) {
            pageables.add(pageable);

            if (ontologyIri.endsWith("ShoplukEmbroidery"))
                return new PageImpl<>(
                        List.of(evidence(), evidence(), evidence()),
                        pageable,
                        3
                );

            return new PageImpl<>(List.of(), pageable, 0);
        }

        private ArchiveEvidenceDetails evidence() {
            return new ArchiveEvidenceDetails(
                    9L,
                    ArchiveType.EMBROIDERY_SAMPLE,
                    null,
                    "Archive item",
                    null,
                    null,
                    null,
                    TrustedLevel.VERIFIED,
                    true,
                    List.of(),
                    null
            );
        }
    }
}
