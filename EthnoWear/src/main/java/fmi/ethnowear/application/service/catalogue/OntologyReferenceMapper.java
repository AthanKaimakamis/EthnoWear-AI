package fmi.ethnowear.application.service.catalogue;

import fmi.ethnowear.api.dto.catalogue.OntologyReferenceDetails;
import fmi.ethnowear.ontology.model.LocalizedOntologyResource;
import fmi.ethnowear.ontology.model.OntologyResource;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class OntologyReferenceMapper {

    public List<OntologyReferenceDetails> toDetails(
            List<OntologyResource> resources,
            List<LocalizedOntologyResource> localizedResources
    ) {
        Map<String, LocalizedOntologyResource> localizedByName = localizedResources.stream()
                .collect(Collectors.toMap(
                        LocalizedOntologyResource::localName,
                        Function.identity(),
                        (first, ignored) -> first
                ));

        return resources.stream()
                .map(resource -> toDetails(resource, localizedByName.get(resource.localName())))
                .sorted(Comparator.comparing(
                        OntologyReferenceDetails::localName,
                        String.CASE_INSENSITIVE_ORDER
                ))
                .toList();
    }

    private OntologyReferenceDetails toDetails(
            OntologyResource resource,
            LocalizedOntologyResource localized
    ) {
        return new OntologyReferenceDetails(
                resource.iri(),
                resource.localName(),
                localized == null ? resource.label() : localized.label()
        );
    }
}
