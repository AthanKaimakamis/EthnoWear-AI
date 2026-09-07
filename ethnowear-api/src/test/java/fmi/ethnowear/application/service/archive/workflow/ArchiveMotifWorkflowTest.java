package fmi.ethnowear.application.service.archive.workflow;

import fmi.ethnowear.application.dto.archive.item.ArchiveItemWriteDto;
import fmi.ethnowear.application.dto.archive.item.ArchiveItemFeatureWriteDto;
import fmi.ethnowear.application.dto.archive.workflow.ArchiveEntryWriteDto;
import fmi.ethnowear.application.port.ontology.EmbroideryOntologyClient;
import fmi.ethnowear.application.service.archive.item.*;
import fmi.ethnowear.application.service.archive.media.attachment.ArchiveItemMediaMapper;
import fmi.ethnowear.application.service.archive.media.attachment.ArchiveItemMediaService;
import fmi.ethnowear.domain.model.archive.*;
import fmi.ethnowear.domain.model.ontology.FeatureType;
import fmi.ethnowear.domain.model.ontology.OntologyResource;
import fmi.ethnowear.persistence.jpa.entity.*;
import fmi.ethnowear.persistence.jpa.repository.*;
import fmi.ethnowear.testutil.EntityTestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ArchiveMotifWorkflowTest {
    private final ArchiveItemRepository items = mock(ArchiveItemRepository.class);
    private final ArchiveItemFeatureRepository features = mock(ArchiveItemFeatureRepository.class);
    private final ArchiveItemMediaRepository media = mock(ArchiveItemMediaRepository.class);
    private final SourceReferenceRepository references = mock(SourceReferenceRepository.class);
    private final EmbroideryOntologyClient ontology = mock(EmbroideryOntologyClient.class);
    private final ArchiveItemOntologyValidator validator = new ArchiveItemOntologyValidator(ontology);
    private final ArchiveItem item = new ArchiveItem();
    private ArchiveItemService itemService;
    private ArchiveEntryService entries;

    @BeforeEach
    void setUp() {
        EntityTestUtils.setId(item, 10L);
        item.setPublicationStatus(PublicationStatus.DRAFT);
        item.setArchiveType(ArchiveType.EMBROIDERY_SAMPLE);
        SourceReference reference = new SourceReference();
        EntityTestUtils.setId(reference, 1L);
        when(items.findById(10L)).thenReturn(Optional.of(item));
        when(items.save(any())).thenAnswer(call -> call.getArgument(0));
        when(references.findById(1L)).thenReturn(Optional.of(reference));
        when(ontology.listRegions()).thenReturn(List.of(resource("ElhovoRegion"), resource("OtherRegion")));
        when(ontology.listRegionalMotifTypes()).thenReturn(List.of(resource("ElhovoMotif")));
        when(ontology.listRegionalEmbroideryTypes()).thenReturn(List.of(resource("ElhovoEmbroidery")));
        when(ontology.findRegionForRegionalMotif("ElhovoMotif")).thenReturn(Optional.of(resource("ElhovoRegion")));
        when(ontology.findRegionForRegionalEmbroidery("ElhovoEmbroidery")).thenReturn(Optional.of(resource("ElhovoRegion")));
        itemService = new ArchiveItemService(items, references, new ArchiveItemMapper(),
                new ArchiveItemUsageChecker(features, media), validator, new ArchiveItemWorkflowGuard());
        entries = new ArchiveEntryService(itemService, mock(ArchiveItemFeatureService.class),
                mock(ArchiveItemMediaService.class), features, media,
                new ArchiveItemFeatureMapper(), new ArchiveItemMediaMapper(), mock(ArchiveImageInheritance.class));
    }

    @Test
    void aggregateEndpointSavesMotifWithoutFeaturesAndReportsMissingCategory() throws Exception {
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(
                new fmi.ethnowear.api.controller.archive.admin.AdminArchiveEntryController(entries))
                .setControllerAdvice(new fmi.ethnowear.api.exception.ArchiveApiExceptionHandler()).build();
        var json = new com.fasterxml.jackson.databind.ObjectMapper();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/admin/archive-entries/10")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new ArchiveEntryWriteDto(
                                input("ElhovoRegion", "ElhovoMotif", "ElhovoEmbroidery"), List.of(), List.of()))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.archiveItem.archiveType").value("MOTIF_EXAMPLE"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.features").isEmpty());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/admin/archive-entries/10")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new ArchiveEntryWriteDto(
                                input("ElhovoRegion", null, "ElhovoEmbroidery"), List.of(), List.of()))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        assertEquals("ElhovoMotif", item.getOntologyRegionalMotifLocalName());
    }

    @Test
    void convertsEmbroideryToMotifWithoutObservedFeaturesAndPreservesOptionalEmbroidery() {
        var result = entries.update(10L, new ArchiveEntryWriteDto(
                input("ElhovoRegion", "ElhovoMotif", "ElhovoEmbroidery"), List.of(), List.of()));
        assertEquals(ArchiveType.MOTIF_EXAMPLE, result.archiveItem().archiveType());
        assertEquals("ElhovoEmbroidery", result.archiveItem().ontologyRegionalEmbroideryLocalName());
        assertEquals("ElhovoMotif", result.archiveItem().ontologyRegionalMotifLocalName());
        assertEquals("Inventory", result.archiveItem().inventoryNumber());
        assertEquals("Description", result.archiveItem().descriptionEn());
        assertEquals(1L, result.archiveItem().sourceReferenceId());
        assertTrue(result.features().isEmpty());
    }

    @Test
    void createsMotifWithoutOptionalEmbroideryOrFeatures() {
        var result = entries.create(new ArchiveEntryWriteDto(input("ElhovoRegion", "ElhovoMotif", null), List.of(), List.of()));
        assertEquals(ArchiveType.MOTIF_EXAMPLE, result.archiveItem().archiveType());
        assertNull(result.archiveItem().ontologyRegionalEmbroideryIri());
    }

    @Test
    void requiresRegionAndRegionalMotif() {
        assertEquals("Region is required when regional motif is selected", assertThrows(IllegalArgumentException.class,
                () -> itemService.create(input(null, "ElhovoMotif", null))).getMessage());
        itemService.update(10L, input(null, null, null));
        assertFalse(new ArchivePublicationValidator(items, features, media, validator, mock(ArchiveImageInheritance.class)).validate(10L).ready());
    }

    @Test
    void rejectsMotifFromAnotherRegion() {
        assertEquals("Regional motif does not belong to region: OtherRegion", assertThrows(IllegalArgumentException.class,
                () -> itemService.create(input("OtherRegion", "ElhovoMotif", null))).getMessage());
    }

    @Test
    void rejectsOptionalEmbroideryFromAnotherRegion() {
        when(ontology.findRegionForRegionalEmbroidery("ElhovoEmbroidery")).thenReturn(Optional.of(resource("OtherRegion")));
        assertEquals("Regional embroidery does not belong to region: ElhovoRegion", assertThrows(IllegalArgumentException.class,
                () -> itemService.create(input("ElhovoRegion", "ElhovoMotif", "ElhovoEmbroidery"))).getMessage());
    }

    @Test
    void publishesClassifiedMotifWithNoFeaturesButStillBlocksUnvalidatedObservations() {
        itemService.update(10L, input("ElhovoRegion", "ElhovoMotif", null));
        var publication = new ArchivePublicationValidator(items, features, media, validator, mock(ArchiveImageInheritance.class));
        assertTrue(publication.validate(10L).ready());
        when(features.existsByArchiveItem_IdAndValidatedFalse(10L)).thenReturn(true);
        assertFalse(publication.validate(10L).ready());
    }

    @Test
    void publicationRechecksOntologyRegionCompatibility() {
        itemService.update(10L, input("ElhovoRegion", "ElhovoMotif", null));
        when(ontology.findRegionForRegionalMotif("ElhovoMotif")).thenReturn(Optional.of(resource("OtherRegion")));
        assertFalse(new ArchivePublicationValidator(items, features, media, validator, mock(ArchiveImageInheritance.class)).validate(10L).ready());
    }

    @Test
    void rejectsNewMotifFeaturesButPreservesExistingLegacyLinks() {
        ArchiveItemFeatureService service = new ArchiveItemFeatureService(features, items, references,
                new ArchiveItemFeatureMapper(), new ArchiveItemFeatureUsageChecker(null), new ArchiveItemWorkflowGuard());
        var input = new ArchiveItemFeatureWriteDto(10L, FeatureType.MOTIF, "urn:test#LegacyMotif", "LegacyMotif",
                BigDecimal.ONE, true, "Legacy evidence", 1L);
        assertThrows(IllegalArgumentException.class, () -> service.create(input));
        ArchiveItemFeature legacy = new ArchiveItemFeature();
        EntityTestUtils.setId(legacy, 20L);
        legacy.setArchiveItem(item);
        legacy.setFeatureType(FeatureType.MOTIF);
        legacy.setOntologyIri(input.ontologyIri());
        legacy.setOntologyLocalName(input.ontologyLocalName());
        when(features.findById(20L)).thenReturn(Optional.of(legacy));
        when(features.save(any())).thenAnswer(call -> call.getArgument(0));
        assertEquals("Legacy evidence", service.update(20L, input).notes());
        assertThrows(IllegalArgumentException.class, () -> service.update(20L,
                new ArchiveItemFeatureWriteDto(10L, FeatureType.MOTIF, "urn:test#Other", "Other", null, true, null, null)));
        verify(features, never()).delete(any());
    }

    private ArchiveItemWriteDto input(String region, String motif, String embroidery) {
        return new ArchiveItemWriteDto(1L, "Collection", "Inventory", null, "Motif", null, "Description",
                ArchiveType.MOTIF_EXAMPLE, "Period", "Origin", "Location", TrustedLevel.VERIFIED,
                iri(region), region, iri(embroidery), embroidery, iri(motif), motif);
    }

    private String iri(String localName) {
        return localName == null ? null : "urn:test#" + localName;
    }

    private OntologyResource resource(String localName) {
        return new OntologyResource(iri(localName), localName, localName);
    }
}
