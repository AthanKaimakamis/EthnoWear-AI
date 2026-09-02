package fmi.ethnowear.infrastructure.ontology.jena.admin;

import fmi.ethnowear.application.dto.ontology.admin.OntologyEntityCommand;
import fmi.ethnowear.application.dto.ontology.admin.RegionDerivedTypeSynchronizationDetails;
import fmi.ethnowear.application.exception.OntologyEntityException;
import fmi.ethnowear.domain.constant.ontology.OntologyTerms;
import fmi.ethnowear.domain.model.ontology.OntologyEntityKind;
import fmi.ethnowear.infrastructure.ontology.jena.JenaOntologyStore;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.ontology.HasValueRestriction;
import org.apache.jena.ontology.OntClass;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.RDFS;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class OntologyEntityAdminServiceTest {

    private static final String NAMESPACE =
            "http://www.semanticweb.org/athan/ontologies/2026/4/ethno-wear#";
    private static final String MINIMAL_ONTOLOGY = """
            <?xml version="1.0"?>
            <rdf:RDF
                xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
                xmlns:rdfs="http://www.w3.org/2000/01/rdf-schema#"
                xmlns:owl="http://www.w3.org/2002/07/owl#">
              <owl:Ontology rdf:about="http://www.semanticweb.org/athan/ontologies/2026/4/ethno-wear"/>
              <owl:Class rdf:about="%sRegion"/>
              <owl:Class rdf:about="%sRegionGroup"/>
              <owl:Class rdf:about="%sMotif"/>
              <owl:Class rdf:about="%sOrnament"/>
              <owl:Class rdf:about="%sTechnique"/>
              <owl:Class rdf:about="%sRegionalEmbroidery"/>
              <owl:ObjectProperty rdf:about="%shasRegion"/>
              <owl:ObjectProperty rdf:about="%smotifHasRegion"/>
              <owl:ObjectProperty rdf:about="%sbelongsToRegionGroup"/>
              <owl:ObjectProperty rdf:about="%sregionUsesOrnament"/>
              <owl:ObjectProperty rdf:about="%sregionUsesTechnique"/>
            </rdf:RDF>
            """.formatted(
            NAMESPACE,
            NAMESPACE,
            NAMESPACE,
            NAMESPACE,
            NAMESPACE,
            NAMESPACE,
            NAMESPACE,
            NAMESPACE,
            NAMESPACE,
            NAMESPACE,
            NAMESPACE
    );

    @TempDir
    Path temporaryDirectory;

    private JenaOntologyStore store;
    private OntologyEntityAdminService service;

    @BeforeEach
    void setUp() throws Exception {
        Path ontologyCopy = temporaryDirectory.resolve("EthnoWear.owx");
        Files.writeString(ontologyCopy, MINIMAL_ONTOLOGY);

        store = new JenaOntologyStore(ontologyCopy, NAMESPACE);
        service = new OntologyEntityAdminService(store, ignored -> {
        });
    }

    @Test
    void regionCreationCreatesOwnedEmbroideryAndMotifClasses() {
        service.create(OntologyEntityKind.REGION, regionCommand("ManagedTestRegion"));

        store.read(model -> {
            Resource region = model.getResource(store.uri("ManagedTestRegion"));
            OntClass embroidery = model.getOntClass(store.uri("ManagedTestEmbroidery"));
            OntClass motif = model.getOntClass(store.uri("ManagedTestMotif"));

            assertNotNull(embroidery);
            assertNotNull(motif);
            assertOwned(model, embroidery, region);
            assertOwned(model, motif, region);
            assertEquals("Шевица - Тестов регион", label(model, embroidery, "bg"));
            assertEquals("Embroidery - Test region", label(model, embroidery, "en"));
            assertEquals("Мотив - Тестов регион", label(model, motif, "bg"));
            assertEquals("Motif - Test region", label(model, motif, "en"));
            assertEquals(
                    Set.of("ManagedTestRegion"),
                    restrictionValues(model, embroidery, OntologyTerms.ObjectProperties.HAS_REGION)
            );
            assertEquals(
                    Set.of("ManagedTestRegion"),
                    restrictionValues(model, motif, OntologyTerms.ObjectProperties.MOTIF_HAS_REGION)
            );
            assertTrue(embroidery.hasSuperClass(
                    model.getOntClass(store.uri(OntologyTerms.Classes.REGIONAL_EMBROIDERY)),
                    true
            ));
            OntClass regionalMotif = model.getOntClass(store.uri(OntologyTerms.Classes.REGIONAL_MOTIF));
            assertTrue(motif.hasSuperClass(regionalMotif, true));
            assertTrue(regionalMotif.hasSuperClass(
                    model.getOntClass(store.uri(OntologyTerms.Classes.MOTIF)),
                    true
            ));
            return null;
        });
    }

    @Test
    void regionUpdateChangesOwnedLabelsButPreservesCuratedMetadataAndRestrictions() {
        service.create(OntologyEntityKind.REGION, regionCommand("PreservedRegion"));
        store.write(model -> {
            OntClass embroidery = model.getOntClass(store.uri("PreservedEmbroidery"));
            addRestriction(model, embroidery, OntologyTerms.ObjectProperties.HAS_ORNAMENT, "Kanatitsa");
            addRestriction(model, embroidery, OntologyTerms.ObjectProperties.HAS_TECHNIQUE, "CrossTechnique");
            addRestriction(model, embroidery, OntologyTerms.ObjectProperties.HAS_MOTIF, "TreeMotif");
            embroidery.addProperty(RDFS.comment, model.createLiteral("Curated comment", "en"));
            embroidery.addProperty(
                    model.createProperty("http://www.w3.org/2004/02/skos/core#altLabel"),
                    model.createLiteral("Curated alias", "en")
            );
            return null;
        });

        service.update(
                OntologyEntityKind.REGION,
                "PreservedRegion",
                regionCommand("PreservedRegion", "Обновен регион", "Updated region")
        );

        store.read(model -> {
            OntClass embroidery = model.getOntClass(store.uri("PreservedEmbroidery"));
            assertEquals("Шевица - Обновен регион", label(model, embroidery, "bg"));
            assertEquals("Embroidery - Updated region", label(model, embroidery, "en"));
            assertEquals(Set.of("Kanatitsa"), restrictionValues(
                    model, embroidery, OntologyTerms.ObjectProperties.HAS_ORNAMENT));
            assertEquals(Set.of("CrossTechnique"), restrictionValues(
                    model, embroidery, OntologyTerms.ObjectProperties.HAS_TECHNIQUE));
            assertEquals(Set.of("TreeMotif"), restrictionValues(
                    model, embroidery, OntologyTerms.ObjectProperties.HAS_MOTIF));
            assertEquals("Curated comment", label(model, embroidery, RDFS.comment, "en"));
            assertEquals("Curated alias", label(
                    model,
                    embroidery,
                    model.createProperty("http://www.w3.org/2004/02/skos/core#altLabel"),
                    "en"
            ));
            return null;
        });
    }

    @Test
    void synchronizationIsIdempotentAndCreatesOnlyMissingManagedClass() {
        service.create(OntologyEntityKind.REGION, regionCommand("IdempotentRegion"));

        RegionDerivedTypeSynchronizationDetails unchanged = service.synchronizeRegionDerivedTypes();
        assertTrue(unchanged.unchanged() >= 2);
        assertEquals(0, unchanged.created());
        assertEquals(0, unchanged.updated());

        service.delete(OntologyEntityKind.REGIONAL_MOTIF, "IdempotentMotif");
        RegionDerivedTypeSynchronizationDetails repaired = service.synchronizeRegionDerivedTypes();

        assertEquals(1, repaired.created());
        assertTrue(repaired.unchanged() >= 1);
        assertNotNull(store.read(model -> model.getOntClass(store.uri("IdempotentMotif"))));
    }

    @Test
    void synchronizationRepairsOnlyManagedParentAndRegionRestriction() {
        service.create(OntologyEntityKind.REGION, regionCommand("RepairRegion"));
        store.write(model -> {
            OntClass motif = model.getOntClass(store.uri("RepairMotif"));
            OntClass regionalMotif = model.getOntClass(store.uri(
                    OntologyTerms.Classes.REGIONAL_MOTIF
            ));
            motif.removeSuperClass(regionalMotif);
            motif.addSuperClass(model.getOntClass(store.uri(
                    OntologyTerms.Classes.REGIONAL_EMBROIDERY
            )));
            return null;
        });

        RegionDerivedTypeSynchronizationDetails result =
                service.synchronizeRegionDerivedTypes();

        assertTrue(result.updated() >= 1);
        store.read(model -> {
            OntClass motif = model.getOntClass(store.uri("RepairMotif"));
            assertTrue(motif.hasSuperClass(model.getOntClass(store.uri(
                    OntologyTerms.Classes.REGIONAL_MOTIF
            )), true));
            assertFalse(motif.hasSuperClass(model.getOntClass(store.uri(
                    OntologyTerms.Classes.REGIONAL_EMBROIDERY
            )), true));
            return null;
        });
    }

    @Test
    void synchronizationDoesNotModifyManualRegionalTypes() {
        service.create(OntologyEntityKind.REGION, regionCommand("ManualProtectionRegion"));
        store.write(model -> {
            OntClass manual = model.createClass(store.uri("CuratedRegionalEmbroidery"));
            manual.addSuperClass(model.getOntClass(store.uri(
                    OntologyTerms.Classes.REGIONAL_EMBROIDERY
            )));
            manual.addProperty(RDFS.label, model.createLiteral("Curated", "en"));
            addRestriction(
                    model,
                    manual,
                    OntologyTerms.ObjectProperties.HAS_REGION,
                    "ManualProtectionRegion"
            );
            return null;
        });

        service.synchronizeRegionDerivedTypes();

        store.read(model -> {
            OntClass manual = model.getOntClass(store.uri("CuratedRegionalEmbroidery"));
            assertEquals("Curated", label(model, manual, "en"));
            assertFalse(manual.hasProperty(model.getProperty(store.uri(
                    OntologyTerms.AnnotationProperties.SYSTEM_GENERATED
            ))));
            return null;
        });
    }

    @Test
    void deterministicNameCollisionRejectsAndRollsBackRegionCreation() {
        store.write(model -> model.createClass(store.uri("CollisionEmbroidery")));

        OntologyEntityException exception = assertThrows(
                OntologyEntityException.class,
                () -> service.create(OntologyEntityKind.REGION, regionCommand("CollisionRegion"))
        );

        assertEquals(OntologyEntityException.Reason.ALREADY_EXISTS, exception.getReason());
        assertNull(store.read(model -> model.getIndividual(store.uri("CollisionRegion"))));
        assertNotNull(store.read(model -> model.getOntClass(store.uri("CollisionEmbroidery"))));
        assertNull(store.read(model -> model.getOntClass(store.uri("CollisionMotif"))));
    }

    @Test
    void legacyDeterministicClassWithoutOwnershipMarkersReturnsConflict() {
        store.write(model -> model.createClass(store.uri("LegacyEmbroidery")));
        assertThrows(
                OntologyEntityException.class,
                () -> service.create(OntologyEntityKind.REGION, regionCommand("LegacyRegion"))
        );
    }

    @Test
    void deletionRemovesOnlyOwnedClassesAndManualRegionReferencesBlockDeletion() {
        service.create(OntologyEntityKind.REGION, regionCommand("DeletionRegion"));
        store.write(model -> {
            OntClass manual = model.createClass(store.uri("ManualDeletionEmbroidery"));
            manual.addSuperClass(model.getOntClass(store.uri(
                    OntologyTerms.Classes.REGIONAL_EMBROIDERY
            )));
            addRestriction(
                    model,
                    manual,
                    OntologyTerms.ObjectProperties.HAS_REGION,
                    "DeletionRegion"
            );
            return null;
        });

        OntologyEntityException exception = assertThrows(
                OntologyEntityException.class,
                () -> service.delete(OntologyEntityKind.REGION, "DeletionRegion")
        );

        assertEquals(OntologyEntityException.Reason.IN_USE, exception.getReason());
        assertNotNull(store.read(model -> model.getOntClass(store.uri("DeletionEmbroidery"))));
        assertNotNull(store.read(model -> model.getOntClass(store.uri("DeletionMotif"))));
        assertNotNull(store.read(model -> model.getOntClass(store.uri("ManualDeletionEmbroidery"))));
    }

    @Test
    void deletionRemovesOwnedClassesWhenRegionIsUnused() {
        service.create(OntologyEntityKind.REGION, regionCommand("UnusedDeletionRegion"));

        service.delete(OntologyEntityKind.REGION, "UnusedDeletionRegion");

        assertNull(store.read(model -> model.getIndividual(store.uri("UnusedDeletionRegion"))));
        assertNull(store.read(model -> model.getOntClass(store.uri("UnusedDeletionEmbroidery"))));
        assertNull(store.read(model -> model.getOntClass(store.uri("UnusedDeletionMotif"))));
    }

    @Test
    void sqlUsageGuardBlocksRegionDeletionWithoutRemovingOntologyResources() {
        service.create(OntologyEntityKind.REGION, regionCommand("SqlUsedRegion"));
        OntologyEntityAdminService guardedService = new OntologyEntityAdminService(
                store,
                ignored -> {
                    throw new OntologyEntityException(
                            OntologyEntityException.Reason.IN_USE,
                            "Ontology resource is referenced by SQL data"
                    );
                }
        );

        assertThrows(
                OntologyEntityException.class,
                () -> guardedService.delete(OntologyEntityKind.REGION, "SqlUsedRegion")
        );
        assertNotNull(store.read(model -> model.getIndividual(store.uri("SqlUsedRegion"))));
        assertNotNull(store.read(model -> model.getOntClass(store.uri("SqlUsedEmbroidery"))));
        assertNotNull(store.read(model -> model.getOntClass(store.uri("SqlUsedMotif"))));
    }

    @Test
    void regionLocalNameCannotChangeDuringUpdate() {
        service.create(OntologyEntityKind.REGION, regionCommand("ImmutableRegion"));

        assertThrows(
                OntologyEntityException.class,
                () -> service.update(
                        OntologyEntityKind.REGION,
                        "ImmutableRegion",
                        regionCommand("RenamedRegion")
                )
        );
    }

    private OntologyEntityCommand regionCommand(String localName) {
        return regionCommand(localName, "Тестов регион", "Test region");
    }

    private OntologyEntityCommand regionCommand(
            String localName,
            String labelBg,
            String labelEn
    ) {
        return new OntologyEntityCommand(
                localName,
                labelBg,
                labelEn,
                List.of(),
                List.of(),
                null,
                null,
                null,
                null,
                Set.of(),
                Set.of(),
                Set.of()
        );
    }

    private void assertOwned(
            org.apache.jena.ontology.OntModel model,
            Resource resource,
            Resource region
    ) {
        Property generated = model.getProperty(store.uri(
                OntologyTerms.AnnotationProperties.SYSTEM_GENERATED
        ));
        Literal marker = resource.getRequiredProperty(generated).getLiteral();
        assertEquals(XSDDatatype.XSDboolean.getURI(), marker.getDatatypeURI());
        assertTrue(marker.getBoolean());
        assertEquals(
                region,
                resource.getRequiredProperty(model.getProperty(store.uri(
                        OntologyTerms.AnnotationProperties.GENERATED_FROM_REGION
                ))).getResource()
        );
    }

    private void addRestriction(
            org.apache.jena.ontology.OntModel model,
            OntClass subject,
            String propertyLocalName,
            String valueLocalName
    ) {
        HasValueRestriction restriction = model.createHasValueRestriction(
                null,
                model.getProperty(store.uri(propertyLocalName)),
                model.getResource(store.uri(valueLocalName))
        );
        subject.addSuperClass(restriction);
    }

    private Set<String> restrictionValues(
            org.apache.jena.ontology.OntModel model,
            Resource subject,
            String propertyLocalName
    ) {
        Set<String> result = new java.util.LinkedHashSet<>();
        model.listObjectsOfProperty(subject, RDFS.subClassOf)
                .filterKeep(RDFNode::isResource)
                .mapWith(RDFNode::asResource)
                .forEachRemaining(restriction -> {
                    Statement property = restriction.getProperty(OWL.onProperty);
                    Statement value = restriction.getProperty(OWL.hasValue);
                    if (property != null && value != null
                            && property.getObject().isURIResource()
                            && value.getObject().isURIResource()
                            && propertyLocalName.equals(property.getResource().getLocalName()))
                        result.add(value.getResource().getLocalName());
                });
        return Set.copyOf(result);
    }

    private String label(
            org.apache.jena.ontology.OntModel model,
            Resource resource,
            String language
    ) {
        return label(model, resource, RDFS.label, language);
    }

    private String label(
            org.apache.jena.ontology.OntModel model,
            Resource resource,
            Property property,
            String language
    ) {
        return model.listObjectsOfProperty(resource, property)
                .filterKeep(RDFNode::isLiteral)
                .mapWith(RDFNode::asLiteral)
                .filterKeep(literal -> language.equals(literal.getLanguage()))
                .mapWith(Literal::getString)
                .nextOptional()
                .orElse(null);
    }
}
