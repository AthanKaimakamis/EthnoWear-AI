package fmi.ethnowear.application.service.catalogue;

import fmi.ethnowear.api.dto.catalogue.CategoryLinkDetails;
import fmi.ethnowear.api.dto.catalogue.EntityLinkDetails;
import fmi.ethnowear.application.enums.FeatureType;
import fmi.ethnowear.ontology.model.LocalizedOntologyResource;
import fmi.ethnowear.ontology.model.OntologyResource;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class OntologyReferenceMapper {

    public List<EntityLinkDetails> toEntityLinks(
            FeatureType entityType,
            @NonNull List<OntologyResource> resources,
            List<LocalizedOntologyResource> localizedResources
    ) {
        Map<String, LocalizedOntologyResource> localizedByName = localizedByName(localizedResources);

        return resources.stream()
                .map(resource -> new EntityLinkDetails(
                        entityType,
                        resource.iri(),
                        resource.localName(),
                        label(resource, localizedByName.get(resource.localName()))
                ))
                .sorted(Comparator.comparing(
                        EntityLinkDetails::localName,
                        String.CASE_INSENSITIVE_ORDER
                ))
                .toList();
    }

    public List<CategoryLinkDetails> toCategoryLinks(
            FeatureType targetEntityType,
            @NonNull List<OntologyResource> resources,
            List<LocalizedOntologyResource> localizedResources
    ) {
        Map<String, LocalizedOntologyResource> localizedByName = localizedByName(localizedResources);

        return resources.stream()
                .map(resource -> new CategoryLinkDetails(
                        targetEntityType,
                        resource.iri(),
                        resource.localName(),
                        label(resource, localizedByName.get(resource.localName()))
                ))
                .sorted(Comparator.comparing(
                        CategoryLinkDetails::localName,
                        String.CASE_INSENSITIVE_ORDER
                ))
                .toList();
    }

    private Map<String, LocalizedOntologyResource> localizedByName(@NonNull List<LocalizedOntologyResource> resources) {
        return resources.stream()
                .collect(Collectors.toMap(
                        LocalizedOntologyResource::localName,
                        Function.identity(),
                        (first, ignored) -> first
                ));
    }

    private String label(OntologyResource resource, LocalizedOntologyResource localized) {
        return localized == null
                ? resource.label()
                : localized.label();
    }
}
