package fmi.ethnowear.application.service.archive.item;

import fmi.ethnowear.application.dto.archive.item.ArchiveItemWriteDto;
import fmi.ethnowear.application.port.ontology.EmbroideryOntologyClient;
import fmi.ethnowear.domain.model.ontology.OntologyResource;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import static fmi.ethnowear.util.TextUtils.isBlank;

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
    }

    private void validateIdentity(
            String resourceName,
            String iri,
            String localName,
            Supplier<List<OntologyResource>> resources
    ) {
        boolean missingIri = isBlank(iri);
        boolean missingLocalName = isBlank(localName);

        if(missingIri && missingLocalName)
            return;

        if(missingIri || missingLocalName)
            throw new IllegalArgumentException(resourceName + " IRI and local name must be provided together");

        OntologyResource resource = resources.get()
                .stream()
                .filter(candidate -> localName.equals(candidate.localName()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(resourceName + "dose not exist: " + localName));

        if(!Objects.equals(resource.iri(), iri))
            throw new IllegalArgumentException(resourceName + " IRI dose not match local name: " + localName);
    }
}
