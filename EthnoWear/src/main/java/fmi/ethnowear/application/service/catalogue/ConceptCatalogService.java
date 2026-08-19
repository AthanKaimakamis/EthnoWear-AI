package fmi.ethnowear.application.service.catalogue;

import fmi.ethnowear.application.dto.catalogue.*;
import fmi.ethnowear.application.service.catalogue.mapper.ConceptCatalogMatcher;
import fmi.ethnowear.application.service.catalogue.mapper.EntityCardMapper;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static fmi.ethnowear.util.InMemorySortUtils.toComparator;
import static fmi.ethnowear.util.PageUtils.getPage;
import static fmi.ethnowear.util.TextUtils.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConceptCatalogService {

    private final OntologyEntityDetailReader ontologyReader;
    private final ConceptCatalogMatcher matcher;
    private final ConceptCatalogFacetService facetService;
    private final EntityCardMapper cardMapper;

    public ConceptCatalogResultDetails search(ConceptCatalogQueryDto query, Pageable pageable) {
        validate(query, pageable);

        List<EntityOntologyDetails> textMatches = ontologyReader
                .list(query.entityType(), query.language())
                .stream()
                .filter(entity -> matcher.matchesText(entity, query.searchText()))
                .toList();

        List<CatalogFacetGroupDetails> facets = facetService.build(textMatches, query);

        List<EntityOntologyDetails> matches = textMatches.stream()
                .filter(entity -> matcher.matchesFilters(entity, query))
                .sorted(catalogueComparator(pageable.getSort()))
                .toList();

        Page<EntityOntologyDetails> page = getPage(matches, pageable);

        List<EntityCardDetails> items = page.getContent()
                .stream()
                .map(cardMapper::toDetails)
                .toList();

        return new ConceptCatalogResultDetails(items, PageMetadataDetails.from(page), facets);
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

        matcher.validate(query);
    }
}
