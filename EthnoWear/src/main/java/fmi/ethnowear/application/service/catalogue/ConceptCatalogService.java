package fmi.ethnowear.application.service.catalogue;

import fmi.ethnowear.api.dto.catalogue.ConceptCatalogQueryDto;
import fmi.ethnowear.api.dto.catalogue.EntityOntologyDetails;
import fmi.ethnowear.api.dto.catalogue.OntologyReferenceDetails;
import fmi.ethnowear.application.enums.FeatureType;
import fmi.ethnowear.application.enums.FilterCombinationMode;
import fmi.ethnowear.ontology.embroidery.EmbroideryOntologyClient;
import fmi.ethnowear.ontology.model.OntologyResource;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static fmi.ethnowear.util.InMemorySortUtils.toComparator;
import static fmi.ethnowear.util.PageUtils.getPage;
import static fmi.ethnowear.util.TextUtils.isBlank;
import static fmi.ethnowear.util.TextUtils.values;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConceptCatalogService {

    private final OntologyEntityDetailReader ontologyReader;
    private final EmbroideryOntologyClient ontology;

    public Page<EntityOntologyDetails> search(ConceptCatalogQueryDto query, Pageable pageable) {
        validate(query, pageable);

        Comparator<EntityOntologyDetails> comparator = catalogueComparator(pageable.getSort());

        List<EntityOntologyDetails> matches = ontologyReader
                .list(query.entityType(), query.language())
                .stream()
                .filter(entity -> matchesText(entity, query.searchText()))
                .filter(entity -> matchesFilters(entity, query))
                .sorted(comparator)
                .toList();
        return getPage(matches, pageable);
    }

    private boolean matchesText(EntityOntologyDetails entity, String searchText) {
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

        Stream<String> categoryValues = entity.categories().stream()
                .flatMap(ref -> Stream.of(
                        ref.localName(),
                        ref.label()
                ));

        Stream<String> relationshipValues = entity.relatedEntities()
                .values()
                .stream()
                .flatMap(List::stream)
                .flatMap(ref -> Stream.of(
                        ref.localName(),
                        ref.label()
                ));

        return Stream.of(ownValues, categoryValues, relationshipValues)
                .flatMap(stream -> stream)
                .filter(Objects::nonNull)
                .map(this::normalize)
                .anyMatch(value -> value.contains(search));
    }

    private boolean matchesFilters(EntityOntologyDetails entity, ConceptCatalogQueryDto query) {
        List<Boolean> groupMatches = new ArrayList<>();

        List<String> categories = values(query.categoryLocalNames());
        if (!categories.isEmpty())
            groupMatches.add(matchesReferences(entity.categories(), categories));

        relatedFilters(query.relatedEntityLocalNames())
                .forEach((type, selected) -> groupMatches.add(
                        matchesReferences(
                                entity.relatedEntities().getOrDefault(type, List.of()),
                                selected
                        )
                ));

        relatedFilters(query.relatedCategoryLocalNames())
                .forEach((type, selected) -> groupMatches.add(
                        matchesRelatedCategories(entity, type, selected)
                ));

        if(groupMatches.isEmpty())
            return true;

        FilterCombinationMode mode = query.combinationMode() == null
                ? FilterCombinationMode.AND
                : query.combinationMode();

        return mode == FilterCombinationMode.AND
                ? groupMatches.stream().allMatch(Boolean::booleanValue)
                : groupMatches.stream().anyMatch(Boolean::booleanValue);
    }

    private boolean matchesReferences(List<OntologyReferenceDetails> references, List<String> selectedLocalNames) {
        Set<String> selected = Set.copyOf(selectedLocalNames);

        return references.stream()
                .map(OntologyReferenceDetails::localName)
                .anyMatch(selected::contains);
    }

    private boolean matchesRelatedCategories(EntityOntologyDetails entity, FeatureType relatedType, List<String> selectedCategories) {
        Set<String> selected = Set.copyOf(selectedCategories);

        return entity.relatedEntities()
                .getOrDefault(relatedType, List.of())
                .stream()
                .map(OntologyReferenceDetails::localName)
                .flatMap(localName -> categoriesOf(relatedType, localName).stream())
                .map(OntologyResource::localName)
                .anyMatch(selected::contains);
    }

    private List<OntologyResource> categoriesOf(FeatureType entityType, String localName) {
        return switch (entityType) {
            case REGION -> ontology.findRegionGroupForRegion(localName)
                    .map(List::of)
                    .orElse(List.of());
            case ORNAMENT -> ontology.listTypesOfOrnament(localName);
            case TECHNIQUE -> ontology.listTypesOfTechnique(localName);
            default -> List.of();
        };
    }

    private Map<FeatureType, List<String>> relatedFilters(Map<FeatureType, List<String>> filters) {
        if (filters == null)
            return Map.of();

        if(filters.keySet().stream().anyMatch(Objects::isNull))
            throw new IllegalArgumentException("Related ontology entity type cannot be null");

        return filters.entrySet()
                .stream()
                .filter(entry -> !values(entry.getValue()).isEmpty())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> values(entry.getValue()))
                );
    }

    private String normalize(String value) {
        return value == null
                ? ""
                : value.trim().toLowerCase(Locale.ROOT);
    }

    private @NonNull Comparator<EntityOntologyDetails> catalogueComparator(Sort sort) {
        Comparator<EntityOntologyDetails> byLabel = Comparator.comparing(entity -> normalize(entity.label()));
        Comparator<EntityOntologyDetails> byLocalName = Comparator.comparing(entity -> normalize(entity.localName()));
        Map<String, Comparator<EntityOntologyDetails>> supportedProperties = Map.of("label", byLabel, "localName", byLocalName);

        return toComparator(sort, supportedProperties, byLabel, byLocalName);
    }

    private void validate(ConceptCatalogQueryDto query, Pageable pageable) {
        if (query == null)
            throw new IllegalArgumentException("Catalogue query is required");

        if (query.entityType() == null)
            throw new IllegalArgumentException("Ontology entity type is required");

        if (pageable == null)
            throw new IllegalArgumentException("Pageable is required");
    }
}
