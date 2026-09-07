package fmi.ethnowear.application.service.archive.workflow;

import fmi.ethnowear.domain.model.document.figure.FigureReviewState;
import fmi.ethnowear.domain.model.ontology.FeatureType;
import fmi.ethnowear.domain.model.rights.RightsStatus;
import fmi.ethnowear.persistence.jpa.entity.*;
import fmi.ethnowear.persistence.jpa.repository.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ArchiveImageInheritanceTest {
    @Test
    void deduplicatesManualLinksKeepsOriginsAndNeverWritesFeatureCopies() {
        var media = mock(ArchiveItemMediaRepository.class);
        var links = mock(MediaEntityLinkRepository.class);
        var first = asset(1L, RightsStatus.UNKNOWN);
        var second = asset(2L, RightsStatus.UNKNOWN);
        var attachments = List.of(attachment(first), attachment(second));
        var observationsToReturn = List.of(link(first, FeatureType.ORNAMENT), link(second, FeatureType.ORNAMENT), link(first, FeatureType.MOTIF));
        when(media.findByArchiveItemId(10L)).thenReturn(attachments);
        when(links.findByMediaAsset_IdIn(List.of(1L, 2L))).thenReturn(observationsToReturn);
        var service = new ArchiveImageInheritance(media, links);
        var observations = service.observations(10L, false);
        assertEquals(1, observations.size());
        assertEquals(2, observations.getFirst().origins().size());
        when(media.findByArchiveItemId(10L)).thenReturn(List.of());
        assertTrue(service.observations(10L, false).isEmpty());
    }

    @Test
    void unknownRightsCannotLeakIntoPublicObservations() {
        var media = mock(ArchiveItemMediaRepository.class);
        var links = mock(MediaEntityLinkRepository.class);
        var attachments = List.of(attachment(asset(1L, RightsStatus.UNKNOWN)));
        when(media.findPublicByArchiveItemId(10L, FigureReviewState.APPROVED)).thenReturn(attachments);
        assertTrue(new ArchiveImageInheritance(media, links).observations(10L, true).isEmpty());
        verifyNoInteractions(links);
    }

    private MediaAsset asset(Long id, RightsStatus status) {
        var asset = mock(MediaAsset.class);
        when(asset.getId()).thenReturn(id);
        when(asset.getRightsStatus()).thenReturn(status);
        when(asset.getFileName()).thenReturn("image.jpg");
        return asset;
    }

    private ArchiveItemMedia attachment(MediaAsset asset) {
        var attachment = mock(ArchiveItemMedia.class);
        when(attachment.getMediaAsset()).thenReturn(asset);
        return attachment;
    }

    private MediaEntityLink link(MediaAsset asset, FeatureType type) {
        var link = mock(MediaEntityLink.class);
        when(link.getMediaAsset()).thenReturn(asset);
        when(link.getEntityType()).thenReturn(type);
        when(link.getOntologyIri()).thenReturn("urn:test:ornament");
        when(link.getOntologyLocalName()).thenReturn("Ornament");
        return link;
    }
}
