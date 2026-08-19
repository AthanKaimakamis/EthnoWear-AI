package fmi.ethnowear.application.service.catalogue;

import fmi.ethnowear.application.dto.catalogue.CategoryLinkDetails;
import fmi.ethnowear.application.service.catalogue.mapper.OntologyReferenceMapper;
import fmi.ethnowear.domain.model.ontology.FeatureType;
import fmi.ethnowear.application.port.ontology.EmbroideryOntologyClient;
import fmi.ethnowear.domain.model.ontology.LocalizedOntologyResource;
import fmi.ethnowear.domain.model.ontology.OntologyLanguage;
import fmi.ethnowear.domain.model.ontology.OntologyResource;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static fmi.ethnowear.util.TextUtils.isBlank;

@Component
@RequiredArgsConstructor
public class OntologyCategoryReader {

    private final EmbroideryOntologyClient ontology;
    private final OntologyReferenceMapper referenceMapper;

    public List<OntologyResource> findCategories(FeatureType entityType, String localName) {
        if(entityType == null)
            throw new IllegalArgumentException("Ontology entity type is required");

        if(isBlank(localName))
            return List.of();

        return switch (entityType) {
            case REGION -> ontology.findRegionGroupForRegion(localName)
                    .map(List::of)
                    .orElse(List.of());
            case ORNAMENT -> ontology.listTypesOfOrnament(localName);
            case TECHNIQUE -> ontology.listTypesOfTechnique(localName);
            default -> List.of();
        };
    }

    public List<CategoryLinkDetails> findCategoryLinks(FeatureType entityType, String localName, String languageTag) {
        if(isBlank(localName))
            return List.of();

        return findCategoryLinks(entityType, List.of(localName), languageTag);
    }

    public List<CategoryLinkDetails> findCategoryLinks(FeatureType entityType, Collection<String> localNames, String languageTag) {
        if(localNames == null || localNames.isEmpty())
            return List.of();

        Map<String, OntologyResource> categories = new LinkedHashMap<>();

        localNames.stream()
                .filter(localName -> !isBlank(localName))
                .flatMap(localName -> findCategories(entityType, localName).stream())
                .forEach(category -> categories.putIfAbsent(category.localName(), category));

        if(categories.isEmpty())
            return List.of();

        OntologyLanguage language = OntologyLanguage.fromTag(languageTag);

        return referenceMapper.toCategoryLinks(
                entityType,
                List.copyOf(categories.values()),
                localizedCategories(entityType, language)
        );
    }

    private List<LocalizedOntologyResource> localizedCategories(@NonNull FeatureType entityType, OntologyLanguage language) {
        return switch (entityType){
            case REGION -> ontology.listLocalizedRegionGroups(language);
            case ORNAMENT -> ontology.listLocalizedOrnamentTypes(language);
            case TECHNIQUE -> ontology.listLocalizedTechniqueTypes(language);
            default -> List.of();
        };
    }
}
