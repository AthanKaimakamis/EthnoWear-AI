package fmi.ethnowear.application.service.catalogue.mapper;

import fmi.ethnowear.application.dto.catalogue.CategoryLinkDetails;
import fmi.ethnowear.application.dto.catalogue.ConceptCatalogQueryDto;
import fmi.ethnowear.application.dto.catalogue.EntityLinkDetails;
import fmi.ethnowear.application.dto.catalogue.EntityOntologyDetails;
import fmi.ethnowear.application.service.catalogue.OntologyCategoryReader;
import fmi.ethnowear.domain.model.ontology.FeatureType;
import fmi.ethnowear.domain.model.catalogue.FilterCombinationMode;
import fmi.ethnowear.domain.model.ontology.OntologyResource;
import fmi.ethnowear.util.TextUtils;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static fmi.ethnowear.util.TextUtils.*;

@Component
@RequiredArgsConstructor
public class ConceptCatalogMatcher {

    private final OntologyCategoryReader categoryReader;

    public boolean matchesText(EntityOntologyDetails entity, String searchText) {
        if(isBlank(searchText))
            return true;

        String search = normalize(searchText);

        Stream<String> ownValues = Stream.concat(
                Stream.of(
                        entity.localName(),
                        entity.label(),
                        entity.comment()
                ),
                entity.altLabels().stream()
        );

        Stream<String> categoryValues = entity.categories()
                .stream()
                .flatMap(category -> Stream.of(
                        category.localName(),
                        category.label()
                ));

        Stream<String> relationshipValues = entity.relatedEntities()
                .values()
                .stream()
                .flatMap(List::stream)
                .flatMap(reference -> Stream.of(
                        reference.localName(),
                        reference.label()
                ));

        return Stream.of(
                        ownValues,
                        categoryValues,
                        relationshipValues
                )
                .flatMap(stream -> stream)
                .filter(Objects::nonNull)
                .map(TextUtils::normalize)
                .anyMatch(value -> value.contains(search));
    }

    public boolean matchesFilters(EntityOntologyDetails entity, @NonNull ConceptCatalogQueryDto query) {
        List<Boolean> groupMatches = new ArrayList<>();

        List<String> categories = values(query.categoryLocalNames());
        if(!categories.isEmpty())
            groupMatches.add(matchesCategories(
                    entity.categories(),
                    categories
            ));

        relatedFilters(query.relatedEntityLocalNames())
                .forEach((type, selected) ->
                        groupMatches.add(matchesEntities(
                                entity.relatedEntities()
                                        .getOrDefault(type, List.of()),
                                selected
                        ))
                );

        relatedFilters(query.relatedCategoryLocalNames())
                .forEach((type, selected) ->
                        groupMatches.add(matchesRelatedCategories(
                                entity,
                                type,
                                selected
                        ))
                );

        if(groupMatches.isEmpty())
            return true;

        FilterCombinationMode mode = query.combinationMode() == null
                ? FilterCombinationMode.AND
                : query.combinationMode();

        return mode == FilterCombinationMode.AND
                ? groupMatches.stream().allMatch(Boolean::booleanValue)
                : groupMatches.stream().anyMatch(Boolean::booleanValue);
    }

    public void validate(@NonNull ConceptCatalogQueryDto query) {
        validateRelatedFilters(query.relatedEntityLocalNames());
        validateRelatedFilters(query.relatedCategoryLocalNames());
    }

    private boolean matchesCategories(
            @NonNull List<CategoryLinkDetails> references,
            List<String> selectedLocalNames
    ) {
        Set<String> selected = Set.copyOf(selectedLocalNames);

        return references.stream()
                .map(CategoryLinkDetails::localName)
                .anyMatch(selected::contains);
    }

    private boolean matchesEntities(
            @NonNull List<EntityLinkDetails> references,
            List<String> selectedLocalNames
    ) {
        Set<String> selected = Set.copyOf(selectedLocalNames);

        return references.stream()
                .map(EntityLinkDetails::localName)
                .anyMatch(selected::contains);
    }

    private boolean matchesRelatedCategories(
            @NonNull EntityOntologyDetails entity,
            FeatureType relatedType,
            List<String> selectedCategories
    ) {
        Set<String> selected = Set.copyOf(selectedCategories);

        return entity.relatedEntities()
                .getOrDefault(relatedType, List.of())
                .stream()
                .map(EntityLinkDetails::localName)
                .flatMap(localName ->
                        categoryReader.findCategories(
                                relatedType,
                                localName
                        ).stream()
                )
                .map(OntologyResource::localName)
                .anyMatch(selected::contains);
    }

    private Map<FeatureType, List<String>> relatedFilters(
            Map<FeatureType, List<String>> filters
    ) {
        if(filters == null)
            return Map.of();

        return filters.entrySet()
                .stream()
                .filter(entry -> !values(entry.getValue()).isEmpty())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> values(entry.getValue())
                ));
    }

    private void validateRelatedFilters(
            Map<FeatureType, List<String>> filters
    ) {
        if(filters != null
                && filters.keySet().stream().anyMatch(Objects::isNull))
            throw new IllegalArgumentException(
                    "Related ontology entity type cannot be null"
            );
    }
}
