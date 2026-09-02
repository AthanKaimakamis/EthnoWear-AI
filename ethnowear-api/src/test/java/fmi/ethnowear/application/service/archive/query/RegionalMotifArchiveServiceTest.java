package fmi.ethnowear.application.service.archive.query;

import fmi.ethnowear.application.dto.archive.query.ArchiveEvidenceDetails;
import fmi.ethnowear.application.dto.archive.query.RegionalMotifArchiveOverviewDetails;
import fmi.ethnowear.application.dto.catalogue.EntityOntologyDetails;
import fmi.ethnowear.application.service.catalogue.OntologyEntityDetailReader;
import fmi.ethnowear.application.service.catalogue.mapper.EntityCardMapper;
import fmi.ethnowear.domain.model.ontology.FeatureType;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RegionalMotifArchiveServiceTest {

    @Test
    void buildsRegionalMotifSections() {
        EntityOntologyDetails entity = new EntityOntologyDetails(
                FeatureType.REGIONAL_MOTIF,
                "urn:ethnowear#ElhovoMotif",
                "ElhovoMotif",
                "Elhovo motif",
                List.of(),
                null,
                "en",
                List.of(),
                Map.of()
        );
        RegionalMotifArchiveService service = new RegionalMotifArchiveService(
                new StubOntologyReader(entity),
                new EntityCardMapper(),
                new StubEvidenceService()
        );

        RegionalMotifArchiveOverviewDetails result = service.findOverview("en", 4);

        assertEquals("en", result.language());
        assertEquals("ElhovoMotif", result.sections().getFirst().regionalMotif().localName());
    }

    @Test
    void rejectsUnboundedPreview() {
        RegionalMotifArchiveService service = new RegionalMotifArchiveService(
                new StubOntologyReader(null),
                new EntityCardMapper(),
                new StubEvidenceService()
        );

        assertThrows(IllegalArgumentException.class, () -> service.findOverview("bg", 13));
    }

    private static final class StubOntologyReader extends OntologyEntityDetailReader {
        private final EntityOntologyDetails entity;

        private StubOntologyReader(EntityOntologyDetails entity) {
            super(null, null, null);
            this.entity = entity;
        }

        @Override
        public List<EntityOntologyDetails> listSummaries(FeatureType entityType, String languageTag) {
            assertEquals(FeatureType.REGIONAL_MOTIF, entityType);
            return entity == null ? List.of() : List.of(entity);
        }
    }

    private static final class StubEvidenceService extends ArchiveEvidenceService {
        private StubEvidenceService() {
            super(null, null, null, null);
        }

        @Override
        public Page<ArchiveEvidenceDetails> findByOntologyEntity(
                FeatureType entityType,
                String ontologyIri,
                Pageable pageable
        ) {
            assertEquals(FeatureType.REGIONAL_MOTIF, entityType);
            return new PageImpl<>(List.of(), pageable, 0);
        }
    }
}
