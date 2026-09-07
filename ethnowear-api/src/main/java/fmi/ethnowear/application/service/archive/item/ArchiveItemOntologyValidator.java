package fmi.ethnowear.application.service.archive.item;

import fmi.ethnowear.application.dto.archive.item.ArchiveItemWriteDto;
import fmi.ethnowear.application.port.ontology.EmbroideryOntologyClient;
import fmi.ethnowear.domain.model.ontology.OntologyIdentity;
import fmi.ethnowear.domain.model.ontology.OntologyResource;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

@Component
@RequiredArgsConstructor
public class ArchiveItemOntologyValidator {
    public void resolveDerivedClassifications(fmi.ethnowear.persistence.jpa.entity.ArchiveItem item) {
        if (item.getOntologyRegionLocalName() == null) return;
        if (item.getArchiveType() == fmi.ethnowear.domain.model.archive.ArchiveType.MOTIF_EXAMPLE
                && item.getOntologyRegionalMotifLocalName() == null) {
            var matches = ontology.listRegionalMotifTypes().stream().filter(resource ->
                    ontology.findRegionForRegionalMotif(resource.localName())
                            .map(region -> region.localName().equals(item.getOntologyRegionLocalName())).orElse(false)).toList();
            if (matches.size() == 1) {
                item.setOntologyRegionalMotifIri(matches.getFirst().iri());
                item.setOntologyRegionalMotifLocalName(matches.getFirst().localName());
            }
        }
        if (item.getArchiveType() == fmi.ethnowear.domain.model.archive.ArchiveType.EMBROIDERY_SAMPLE
                && item.getOntologyRegionalEmbroideryLocalName() == null) {
            var matches = ontology.listRegionalEmbroideryTypes().stream().filter(resource ->
                    ontology.findRegionForRegionalEmbroidery(resource.localName())
                            .map(region -> region.localName().equals(item.getOntologyRegionLocalName())).orElse(false)).toList();
            if (matches.size() == 1) {
                item.setOntologyRegionalEmbroideryIri(matches.getFirst().iri());
                item.setOntologyRegionalEmbroideryLocalName(matches.getFirst().localName());
            }
        }
    }

    private final EmbroideryOntologyClient ontology;

    public void validateClassifications(@NotNull ArchiveItemWriteDto input) {
        validateClassifications(
                input.ontologyRegionIri(), input.ontologyRegionLocalName(),
                input.ontologyRegionalEmbroideryIri(), input.ontologyRegionalEmbroideryLocalName(),
                input.ontologyRegionalMotifIri(), input.ontologyRegionalMotifLocalName()
        );
    }

    public void validateClassifications(
            String regionIri, String regionLocalName,
            String embroideryIri, String embroideryLocalName,
            String motifIri, String motifLocalName
    ) {
        validateIdentity(
                "Region",
                regionIri,
                regionLocalName,
                ontology::listRegions);

        validateIdentity(
                "Regional embroidery",
                embroideryIri,
                embroideryLocalName,
                ontology::listRegionalEmbroideryTypes
        );

        validateIdentity(
                "Regional motif",
                motifIri,
                motifLocalName,
                ontology::listRegionalMotifTypes
        );

        validateRegionMatch(
                "Regional embroidery",
                embroideryLocalName,
                regionLocalName,
                ontology::findRegionForRegionalEmbroidery
        );
        validateRegionMatch(
                "Regional motif",
                motifLocalName,
                regionLocalName,
                ontology::findRegionForRegionalMotif
        );
    }

    private void validateIdentity(
            String resourceName,
            String iri,
            String localName,
            Supplier<List<OntologyResource>> resources
    ) {
        OntologyIdentity identity = new OntologyIdentity(iri, localName);

        if(identity.isAbsent())
            return;

        if(identity.isIncomplete())
            throw new IllegalArgumentException(resourceName + " IRI and local name must be provided together");

        OntologyResource resource = resources.get()
                .stream()
                .filter(candidate -> identity.localName().equals(candidate.localName()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(resourceName + " does not exist: " + identity.localName()));

        if(!Objects.equals(resource.iri(), identity.iri()))
            throw new IllegalArgumentException(resourceName + " IRI does not match local name: " + identity.localName());
    }

    private void validateRegionMatch(
            String resourceName,
            String resourceLocalName,
            String regionLocalName,
            Function<String, Optional<OntologyResource>> regionResolver
    ) {
        if(resourceLocalName == null)
            return;

        if(regionLocalName == null)
            throw new IllegalArgumentException(
                    "Region is required when " + resourceName.toLowerCase() + " is selected"
            );

        OntologyResource region = regionResolver.apply(resourceLocalName)
                .orElseThrow(() -> new IllegalArgumentException(
                        resourceName + " has no region classification"
                ));
        if(!regionLocalName.equals(region.localName()))
            throw new IllegalArgumentException(
                    resourceName + " does not belong to region: " + regionLocalName
            );
    }
}
