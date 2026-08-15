package fmi.ethnowear.application.dto.catalogue;

import fmi.ethnowear.domain.model.ontology.FeatureType;
import fmi.ethnowear.domain.model.catalogue.FilterCombinationMode;

import java.util.List;
import java.util.Map;

public record ConceptCatalogQueryDto(
        FeatureType entityType,
        String language,
        String searchText,
        List<String> categoryLocalNames,
        Map<FeatureType, List<String>> relatedEntityLocalNames,
        Map<FeatureType, List<String>> relatedCategoryLocalNames,
        FilterCombinationMode combinationMode
) {
}
