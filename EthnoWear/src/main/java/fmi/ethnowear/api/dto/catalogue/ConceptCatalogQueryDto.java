package fmi.ethnowear.api.dto.catalogue;

import fmi.ethnowear.application.enums.FeatureType;
import fmi.ethnowear.application.enums.FilterCombinationMode;

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
