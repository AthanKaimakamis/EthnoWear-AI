package fmi.ethnowear.persistence.jpa.repository;

import fmi.ethnowear.domain.model.ontology.FeatureType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE;

@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
@EnabledIfEnvironmentVariable(named = "ETHNOWEAR_LIVE_DB_TESTS", matches = "true")
class ArchiveMotifPersistenceTest {
    @Autowired private ArchiveItemRepository items;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void groupsPublishedMotifsByClassificationNotFeatureRows() {
        String iri = "urn:ethnowear:test:motif:" + java.util.UUID.randomUUID();
        Long classified = insertItem("PUBLISHED", iri);
        insertItem("DRAFT", iri);
        Long legacy = insertItem("PUBLISHED", null);
        jdbc.update("""
                INSERT INTO ethnowear.ArchiveItemFeatures
                    (ArchiveItemId, FeatureType, OntologyIri, OntologyLocalName, Validated)
                VALUES (?, N'REGIONAL_MOTIF', ?, N'TestMotif', 1)
                """, legacy, iri);

        var result = items.findOntologyEvidence(FeatureType.REGIONAL_MOTIF, iri, false, false, true, PageRequest.of(0, 10));
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent()).extracting(item -> item.getId()).containsExactly(classified);
        assertThat(result.getContent().getFirst().getOntologyRegionalMotifIri()).isEqualTo(iri);
        assertThat(result.getContent().getFirst().getOntologyRegionalEmbroideryIri()).isEqualTo("urn:test#Embroidery");
    }

    private Long insertItem(String status, String motifIri) {
        return jdbc.queryForObject("""
                INSERT INTO ethnowear.ArchiveItems
                    (ArchiveType, TrustedLevel, PublicationStatus, TitleEn,
                     OntologyRegionIri, OntologyRegionLocalName,
                     OntologyRegionalMotifIri, OntologyRegionalMotifLocalName,
                     OntologyRegionalEmbroideryIri, OntologyRegionalEmbroideryLocalName)
                OUTPUT INSERTED.Id
                VALUES (N'MOTIF_EXAMPLE', N'VERIFIED', ?, N'Motif test', N'urn:test#Region', N'Region',
                        ?, ?, N'urn:test#Embroidery', N'Embroidery')
                """, Long.class, status, motifIri, motifIri == null ? null : "TestMotif");
    }
}
