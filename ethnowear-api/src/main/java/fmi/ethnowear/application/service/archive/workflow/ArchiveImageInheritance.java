package fmi.ethnowear.application.service.archive.workflow;

import fmi.ethnowear.domain.model.ontology.FeatureType;
import fmi.ethnowear.domain.model.document.figure.FigureReviewState;
import fmi.ethnowear.persistence.jpa.repository.ArchiveItemMediaRepository;
import fmi.ethnowear.persistence.jpa.repository.MediaEntityLinkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import fmi.ethnowear.application.dto.archive.workflow.InheritedObservationDetails;
import fmi.ethnowear.application.dto.archive.workflow.InheritedObservationDetails.Origin;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ArchiveImageInheritance {
    private final ArchiveItemMediaRepository media;
    private final MediaEntityLinkRepository links;

    public List<InheritedObservationDetails> observations(Long itemId, boolean publicOnly) {
        var attachments = publicOnly ? media.findPublicByArchiveItemId(itemId, FigureReviewState.APPROVED)
                : media.findByArchiveItemId(itemId);
        var ids = attachments.stream().map(value -> value.getMediaAsset())
                .filter(asset -> !publicOnly || (cleared(asset.getRightsStatus(), asset.getLicense())
                        && (asset.getSourceReference() == null || cleared(asset.getSourceReference().getSource().getRightsStatus(),
                        asset.getSourceReference().getSource().getLicense()))))
                .map(asset -> asset.getId()).distinct().toList();
        if (ids.isEmpty()) return List.of();
        var grouped = new LinkedHashMap<String, InheritedObservationDetails>();
        for (var link : links.findByMediaAsset_IdIn(ids)) {
            if (!Set.of(FeatureType.ORNAMENT, FeatureType.TECHNIQUE, FeatureType.COLOR).contains(link.getEntityType())) continue;
            var asset = link.getMediaAsset();
            var origin = new Origin(asset.getId(), asset.getFileName(), asset.getSourceReference() == null ? null : asset.getSourceReference().getId());
            var key = link.getEntityType() + ":" + link.getOntologyIri();
            var previous = grouped.get(key);
            var origins = new ArrayList<Origin>(previous == null ? List.of() : previous.origins());
            if (!origins.contains(origin)) origins.add(origin);
            grouped.put(key, new InheritedObservationDetails(link.getEntityType(), link.getOntologyIri(), link.getOntologyLocalName(), origins));
        }
        return List.copyOf(grouped.values());
    }

    private boolean cleared(fmi.ethnowear.domain.model.rights.RightsStatus status, String license) {
        return status == fmi.ethnowear.domain.model.rights.RightsStatus.PUBLIC_DOMAIN
                || (status == fmi.ethnowear.domain.model.rights.RightsStatus.LICENSED && license != null && !license.isBlank());
    }
}
