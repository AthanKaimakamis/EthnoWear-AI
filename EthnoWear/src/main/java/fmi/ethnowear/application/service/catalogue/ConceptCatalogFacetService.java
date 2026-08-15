package fmi.ethnowear.application.service.catalogue;

import fmi.ethnowear.api.dto.catalogue.*;
import fmi.ethnowear.application.enums.CatalogFacetType;
import fmi.ethnowear.application.enums.FeatureType;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Unmodifiable;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;

import java.util.*;

import static fmi.ethnowear.util.TextUtils.normalize;
import static fmi.ethnowear.util.TextUtils.values;

@Service
@RequiredArgsConstructor
public class ConceptCatalogFacetService {

    private final ConceptCatalogMatcher matcher;
    private final OntologyCategoryReader categoryReader;

    public List<CatalogFacetGroupDetails> build(List<EntityOntologyDetails> entities, ConceptCatalogQueryDto query) {
        List<CatalogFacetGroupDetails> groups = new ArrayList<>();

        addCategoryFacet(groups, entities, query);
        addRelatedEntityFacets(groups, entities, query);
        addRelatedCategoryFacets(groups, entities, query);

        return List.copyOf(groups);
    }

    private void addCategoryFacet(
            List<CatalogFacetGroupDetails> groups,
            @NonNull List<EntityOntologyDetails> entities,
            ConceptCatalogQueryDto query
    ) {
        Map<String, FacetCandidate> candidates = new LinkedHashMap<>();

        entities.stream()
                .flatMap(entity -> entity.categories().stream())
                .forEach(category -> candidates
                        .putIfAbsent(category.localName(), candidate(category))
                );

        addGroup(groups, CatalogFacetType.CATEGORY, query.entityType(), candidates.values(), entities, query);
    }

    private void addRelatedEntityFacets(
            List<CatalogFacetGroupDetails> groups,
            @NonNull List<EntityOntologyDetails> entities,
            ConceptCatalogQueryDto query
    ) {
        Map<FeatureType, Map<String, FacetCandidate>> candidates = new EnumMap<>(FeatureType.class);

        entities.forEach(entity -> entity.relatedEntities()
                .forEach((type, references) -> {
                    Map<String, FacetCandidate> candidatesByName = candidates
                            .computeIfAbsent(type, ignored -> new LinkedHashMap<>());

                    references.forEach(reference -> candidatesByName
                            .putIfAbsent(reference.localName(), candidate(reference)));
                })
        );

        candidates.forEach((type, candidatesByName) ->
                addGroup(groups, CatalogFacetType.RELATED_ENTITY, type, candidatesByName.values(), entities, query));
    }

    private void addRelatedCategoryFacets(
            List<CatalogFacetGroupDetails> groups,
            @NonNull List<EntityOntologyDetails> entities,
            ConceptCatalogQueryDto query
    ) {
        Map<FeatureType, List<String>> relatedLocalNames = new EnumMap<>(FeatureType.class);

        entities.forEach(entity -> entity.relatedEntities()
                .forEach((type, reference) -> relatedLocalNames
                        .computeIfAbsent(type, ignored -> new ArrayList<>())
                        .addAll(reference.stream()
                                .map(EntityLinkDetails::localName)
                                .toList())
                )
        );

        relatedLocalNames.forEach((type, localName) -> {
            List<CategoryLinkDetails> categories = categoryReader
                    .findCategoryLinks(type, localName, query.language());

            Map<String, FacetCandidate> candidates = new LinkedHashMap<>();

            categories.forEach(category -> candidates
                    .putIfAbsent(category.localName(), candidate(category))
            );

            addGroup(groups, CatalogFacetType.RELATED_CATEGORY, type, candidates.values(), entities, query);
        });
    }

    private void addGroup(
            List<CatalogFacetGroupDetails> groups,
            CatalogFacetType facetType,
            FeatureType entityType,
            @NonNull Collection<FacetCandidate> candidates,
            List<EntityOntologyDetails> entities,
            ConceptCatalogQueryDto query
    ) {
        if (candidates.isEmpty())
            return;

        List<CatalogFacetValueDetails> facetValues = candidates.stream()
                .map(candidate -> toDetails(
                        candidate,
                        facetType,
                        entityType,
                        entities,
                        query
                ))
                .sorted(Comparator
                        .comparing((CatalogFacetValueDetails value) -> normalize(value.label()))
                        .thenComparing(value -> normalize(value.localName()))
                )
                .toList();

        groups.add(new CatalogFacetGroupDetails(facetType, entityType, facetValues));
    }

    private @NonNull CatalogFacetValueDetails toDetails(
            @NonNull FacetCandidate candidate,
            CatalogFacetType facetType,
            FeatureType entityType,
            @NonNull List<EntityOntologyDetails> entities,
            ConceptCatalogQueryDto query
    ) {
        boolean selected = isSelected(query, facetType, entityType, candidate.localName());

        ConceptCatalogQueryDto prospectiveQuery = selected
                ? query
                : select(query, facetType, entityType, candidate.localName());

        long count = entities.stream()
                .filter(entity -> matcher.matchesFilters(entity, prospectiveQuery))
                .count();

        return new CatalogFacetValueDetails(
                candidate.iri(),
                candidate.localName(),
                candidate.label(),
                count,
                selected
        );
    }

    private boolean isSelected(
            ConceptCatalogQueryDto query,
            @NonNull CatalogFacetType facetType,
            FeatureType entityType,
            String localName
    ) {
        return switch (facetType) {
            case CATEGORY -> values(query.categoryLocalNames()).contains(localName);
            case RELATED_ENTITY -> selectedValues(query.relatedEntityLocalNames(), entityType).contains(localName);
            case RELATED_CATEGORY -> selectedValues(query.relatedCategoryLocalNames(), entityType).contains(localName);
        };
    }

    @Contract("_, _, _, _ -> new")
    private @NonNull ConceptCatalogQueryDto select(
            @NonNull ConceptCatalogQueryDto query,
            @NonNull CatalogFacetType facetType,
            FeatureType entityType,
            String localName
    ) {
        List<String> categories = query.categoryLocalNames();
        Map<FeatureType, List<String>> relatedEntities = query.relatedEntityLocalNames();
        Map<FeatureType, List<String>> relatedCategories = query.relatedCategoryLocalNames();

        switch (facetType) {
            case CATEGORY -> categories = withValue(categories, localName);
            case RELATED_ENTITY -> relatedEntities = withValue(relatedEntities, entityType, localName);
            case RELATED_CATEGORY -> relatedCategories = withValue(relatedCategories, entityType, localName);
        }

        return new ConceptCatalogQueryDto(
                query.entityType(),
                query.language(),
                query.searchText(),
                categories,
                relatedEntities,
                relatedCategories,
                query.combinationMode()
        );
    }

    private List<String> selectedValues(Map<FeatureType, List<String>> selections, FeatureType entityType) {
        if (selections == null)
            return List.of();

        return values(selections.get(entityType));
    }

    private @NonNull @Unmodifiable List<String> withValue(List<String> current, String value) {
        List<String> result = new ArrayList<>(values(current));

        if (!result.contains(value))
            result.add(value);

        return List.copyOf(result);
    }

    private @NonNull @Unmodifiable Map<FeatureType, List<String>> withValue(
            Map<FeatureType, List<String>> current,
            FeatureType entityType,
            String value
    ) {
        Map<FeatureType, List<String>> result = new EnumMap<>(FeatureType.class);

        if (current != null)
            current.forEach((type, selected) ->
                    result.put(type, values(selected))
            );

        result.put(entityType, withValue(result.get(entityType), value));

        return Map.copyOf(result);
    }

    @Contract("_ -> new")
    private @NonNull FacetCandidate candidate(@NonNull CategoryLinkDetails category) {
        return new FacetCandidate(
                category.iri(),
                category.localName(),
                category.label()
        );
    }

    @Contract("_ -> new")
    private @NonNull FacetCandidate candidate(@NonNull EntityLinkDetails reference) {
        return new FacetCandidate(
                reference.iri(),
                reference.localName(),
                reference.label()
        );
    }

    private record FacetCandidate(
            String iri,
            String localName,
            String label
    ) {

    }
}
