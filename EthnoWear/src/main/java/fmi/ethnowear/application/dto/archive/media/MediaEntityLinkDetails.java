package fmi.ethnowear.application.dto.archive.media;

import fmi.ethnowear.application.dto.IdentifiableDto;
import fmi.ethnowear.domain.model.ontology.FeatureType;
import java.time.LocalDateTime;

public record MediaEntityLinkDetails(Long id, Long mediaAssetId, FeatureType entityType,
                                     String ontologyIri, String ontologyLocalName,
                                     String description, LocalDateTime createdAt,
                                     LocalDateTime updatedAt) implements IdentifiableDto { }
