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

    private final EmbroideryOntologyClient ontology;

    public void validateClassifications(@NotNull ArchiveItemWriteDto input) {
        validateIdentity(
                "Region",
                input.ontologyRegionIri(),
                input.ontologyRegionLocalName(),
                ontology::listRegions);

        validateIdentity(
                "Regional embroidery",
                input.ontologyRegionalEmbroideryIri(),
                input.ontologyRegionalEmbroideryLocalName(),
                ontology::listRegionalEmbroideryTypes
        );

        validateIdentity(
                "Regional motif",
                input.ontologyRegionalMotifIri(),
                input.ontologyRegionalMotifLocalName(),
                ontology::listRegionalMotifTypes
        );

        validateRegionMatch(
                "Regional embroidery",
                input.ontologyRegionalEmbroideryLocalName(),
                input.ontologyRegionLocalName(),
                ontology::findRegionForRegionalEmbroidery
        );
        validateRegionMatch(
                "Regional motif",
                input.ontologyRegionalMotifLocalName(),
                input.ontologyRegionLocalName(),
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
