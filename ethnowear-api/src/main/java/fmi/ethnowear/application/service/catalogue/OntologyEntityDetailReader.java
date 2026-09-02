package fmi.ethnowear.application.service.catalogue;

import fmi.ethnowear.application.dto.catalogue.EntityLinkDetails;
import fmi.ethnowear.application.dto.catalogue.EntityOntologyDetails;
import fmi.ethnowear.application.service.catalogue.mapper.OntologyReferenceMapper;
import fmi.ethnowear.domain.model.ontology.FeatureType;
import fmi.ethnowear.application.exception.OntologyEntityNotFoundException;
import fmi.ethnowear.application.port.ontology.EmbroideryOntologyClient;
import fmi.ethnowear.domain.model.ontology.LocalizedOntologyResource;
import fmi.ethnowear.domain.model.ontology.OntologyLanguage;
import fmi.ethnowear.domain.model.ontology.OntologyResource;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Unmodifiable;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static fmi.ethnowear.util.TextUtils.isBlank;

@Component
@RequiredArgsConstructor
public class OntologyEntityDetailReader {

    private final EmbroideryOntologyClient ontology;
    private final OntologyReferenceMapper referenceMapper;
    private final OntologyCategoryReader categoryReader;

    public EntityOntologyDetails find(FeatureType entityType, String localName, String languageTag) {
        validate(entityType, localName);

        OntologyLanguage language = OntologyLanguage.fromTag(languageTag);
        Map<FeatureType, List<LocalizedOntologyResource>> localizedCache = new EnumMap<>(FeatureType.class);
        LocalizedOntologyResource entity = localized(localizedCache, entityType, language)
                .stream()
                .filter(resource -> resource.localName().equals(localName))
                .findFirst()
                .orElseThrow(() -> new OntologyEntityNotFoundException(entityType, localName));

        return toDetails(entityType, entity, language, localizedCache);
    }

    public List<EntityOntologyDetails> list(FeatureType entityType, String languageTag) {
        if(entityType == null)
            throw new IllegalArgumentException("Ontology entity type is required");

        OntologyLanguage language = OntologyLanguage.fromTag(languageTag);
        Map<FeatureType, List<LocalizedOntologyResource>> localizedCache = new EnumMap<>(FeatureType.class);

        return localized(localizedCache, entityType, language).stream()
                .map(entity -> toDetails(entityType, entity, language, localizedCache))
                .toList();
    }

    public List<EntityOntologyDetails> listSummaries(FeatureType entityType, String languageTag) {
        if(entityType == null)
            throw new IllegalArgumentException("Ontology entity type is required");

        OntologyLanguage language = OntologyLanguage.fromTag(languageTag);
        Map<FeatureType, List<LocalizedOntologyResource>> localizedCache = new EnumMap<>(FeatureType.class);

        return localized(localizedCache, entityType, language).stream()
                .map(entity -> new EntityOntologyDetails(
                        entityType,
                        entity.iri(),
                        entity.localName(),
                        entity.label(),
                        entity.altLabels(),
                        entity.comment(),
                        language.tag(),
                        List.of(),
                        Map.of()
                ))
                .toList();
    }

    private @NonNull @Unmodifiable Map<FeatureType, List<EntityLinkDetails>> relationships(
            @NonNull FeatureType entityType,
            String localName,
            OntologyLanguage language,
            Map<FeatureType, List<LocalizedOntologyResource>> localizedCache
    ) {
        Map<FeatureType, List<EntityLinkDetails>> result = new EnumMap<>(FeatureType.class);

        switch (entityType) {
            case REGION -> {
                put(result, FeatureType.ORNAMENT, ontology.listOrnamentsUsedByRegion(localName), language, localizedCache);
                put(result, FeatureType.COLOR, ontology.listColorsUsedByRegion(localName), language, localizedCache);
                put(result, FeatureType.TECHNIQUE, ontology.listTechniquesUsedByRegion(localName), language, localizedCache);
                put(result, FeatureType.REGIONAL_EMBROIDERY, ontology.listRegionalEmbroideriesForRegion(localName), language, localizedCache);
                put(result, FeatureType.REGIONAL_MOTIF, ontology.listRegionalMotifsForRegion(localName), language, localizedCache);
            }
            case MOTIF -> {
                put(result, FeatureType.REGION, ontology.listRegionsOfMotif(localName), language, localizedCache);
                put(result, FeatureType.ORNAMENT, ontology.listOrnamentOfMotif(localName), language, localizedCache);
                put(result, FeatureType.COLOR, ontology.listColorsOfMotif(localName), language, localizedCache);
                put(result, FeatureType.TECHNIQUE, ontology.listTechniquesOfMotif(localName), language, localizedCache);
                put(result, FeatureType.REGIONAL_EMBROIDERY, ontology.listRegionalEmbroideriesUsingMotif(localName), language, localizedCache);
            }
            case REGIONAL_EMBROIDERY -> {
                ontology.findRegionForRegionalEmbroidery(localName)
                        .ifPresent(region -> put(result, FeatureType.REGION, List.of(region), language, localizedCache));
                put(result, FeatureType.ORNAMENT, ontology.listOrnamentsOfEmbroidery(localName), language, localizedCache);
                put(result, FeatureType.COLOR, ontology.listColorsOfEmbroidery(localName), language, localizedCache);
                put(result, FeatureType.TECHNIQUE, ontology.listTechniquesOfEmbroidery(localName), language, localizedCache);
                put(result, FeatureType.MOTIF, ontology.listMotifsOfEmbroidery(localName), language, localizedCache);
            }
            case REGIONAL_MOTIF -> ontology.findRegionForRegionalMotif(localName)
                    .ifPresent(region -> put(result, FeatureType.REGION, List.of(region), language, localizedCache));
            case ORNAMENT -> {
                put(result, FeatureType.REGION, ontology.listRegionsUsingOrnament(localName), language, localizedCache);
                put(result, FeatureType.MOTIF, ontology.listMotifsUsingOrnament(localName), language, localizedCache);
                put(result, FeatureType.REGIONAL_EMBROIDERY, ontology.listRegionalEmbroideriesUsingOrnament(localName), language, localizedCache);
            }
            case TECHNIQUE -> {
                put(result, FeatureType.REGION, ontology.listRegionsUsingTechnique(localName), language, localizedCache);
                put(result, FeatureType.MOTIF, ontology.listMotifsUsingTechnique(localName), language, localizedCache);
                put(result, FeatureType.REGIONAL_EMBROIDERY, ontology.listRegionalEmbroideriesUsingTechnique(localName), language, localizedCache);
            }
            case COLOR -> {
                put(result, FeatureType.REGION, ontology.listRegionsUsingColor(localName), language, localizedCache);
                put(result, FeatureType.MOTIF, ontology.listMotifsUsingColor(localName), language, localizedCache);
                put(result, FeatureType.REGIONAL_EMBROIDERY, ontology.listRegionalEmbroideriesUsingColor(localName), language, localizedCache);
            }
        }

        return Collections.unmodifiableMap(new EnumMap<>(result));
    }

    private void put(
            Map<FeatureType, List<EntityLinkDetails>> result,
            FeatureType targetType,
            @NonNull List<OntologyResource> resources,
            OntologyLanguage language,
            Map<FeatureType, List<LocalizedOntologyResource>> localizedCache
    ) {
        if(resources.isEmpty())
            return;

        result.put(
                targetType,
                referenceMapper.toEntityLinks(
                        targetType,
                        resources,
                        localized(localizedCache, targetType, language)
                )
        );
    }

    private List<LocalizedOntologyResource> localized(
            @NonNull Map<FeatureType, List<LocalizedOntologyResource>> localizedCache,
            @NonNull FeatureType entityType,
            OntologyLanguage language
    ) {
        return localizedCache.computeIfAbsent(entityType, ignored -> switch (entityType) {
            case REGION -> ontology.listLocalizedRegions(language);
            case ORNAMENT -> ontology.listLocalizedOrnaments(language);
            case COLOR -> ontology.listLocalizedColors(language);
            case TECHNIQUE -> ontology.listLocalizedTechniques(language);
            case MOTIF -> ontology.listLocalizedMotifs(language);
            case REGIONAL_EMBROIDERY -> ontology.listLocalizedRegionalEmbroideryTypes(language);
            case REGIONAL_MOTIF -> ontology.listLocalizedRegionalMotifTypes(language);
        });
    }

    private void validate(FeatureType entityItem, String localName) {
        if (entityItem == null)
            throw new IllegalArgumentException("Ontology entity type is required");

        if (isBlank(localName))
            throw new IllegalArgumentException("Ontology local name is required");
    }

    @Contract("_, _, _, _ -> new")
    private @NonNull EntityOntologyDetails toDetails(
            FeatureType entityType,
            @NonNull LocalizedOntologyResource entity,
            @NonNull OntologyLanguage language,
            Map<FeatureType, List<LocalizedOntologyResource>> localizedCache
    ) {
        return new EntityOntologyDetails(
                entityType,
                entity.iri(),
                entity.localName(),
                entity.label(),
                entity.altLabels(),
                entity.comment(),
                language.tag(),
                categoryReader.findCategoryLinks(
                        entityType,
                        entity.localName(),
                        language.tag()
                ),
                relationships(
                        entityType,
                        entity.localName(),
                        language,
                        localizedCache
                )
        );
    }
}
