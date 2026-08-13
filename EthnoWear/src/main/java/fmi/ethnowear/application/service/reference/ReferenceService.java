package fmi.ethnowear.application.service.reference;

import fmi.ethnowear.api.dto.reference.ReferenceItemDto;
import fmi.ethnowear.api.dto.reference.ReferenceResponse;
import fmi.ethnowear.ontology.embroidery.EmbroideryOntologyClient;
import fmi.ethnowear.ontology.model.LocalizedOntologyResource;
import fmi.ethnowear.ontology.model.OntologyLanguage;
import org.jetbrains.annotations.Unmodifiable;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

import java.util.function.Function;


@Service
public class ReferenceService {

    private final EmbroideryOntologyClient ontology;

    public ReferenceService(EmbroideryOntologyClient ontology) {
        this.ontology = ontology;
    }

    public ReferenceResponse getFullReference(String languageTag) {
        OntologyLanguage language = OntologyLanguage.fromTag(languageTag);

        return new ReferenceResponse(
                language.tag(),
                getRegions(language.tag()),
                getRegionGroups(language.tag()),
                getOrnaments(language.tag()),
                getOrnamentTypes(language.tag()),
                getColors(language.tag()),
                getTechniques(language.tag()),
                getTechniqueTypes(language.tag()),
                getMotifs(language.tag()),
                getRegionalEmbroideryTypes(language.tag()),
                getRegionsByRegionGroup(),
                getRegionByRegionalEmbroidery(),
                getOrnamentsByRegion(),
                getTechniquesByRegion(),
                getOrnamentsByType(),
                getTechniquesByType()
        );
    }

    public Map<String, List<String>> getRegionsByRegionGroup() {
        Map<String, List<String>> result = new LinkedHashMap<>();

        ontology.listRegionGroups().forEach(group -> result.put(
                group.localName(),
                ontology.listRegionsInGroup(group.localName()).stream()
                        .map(region -> region.localName())
                        .toList()
        ));

        return result;
    }

    public Map<String, String> getRegionByRegionalEmbroidery() {
        Map<String, String> result = new LinkedHashMap<>();

        ontology.listRegionalEmbroideryTypes().forEach(embroidery ->
                ontology.findRegionForRegionalEmbroidery(embroidery.localName())
                        .ifPresent(region -> result.put(embroidery.localName(), region.localName()))
        );

        return result;
    }

    public Map<String, List<String>> getOrnamentsByRegion() {
        Map<String, List<String>> result = new LinkedHashMap<>();

        ontology.listRegions().forEach(region -> result.put(
                region.localName(),
                ontology.listOrnamentsUsedByRegion(region.localName()).stream()
                        .map(ornament -> ornament.localName())
                        .toList()
        ));

        return result;
    }

    public Map<String, List<String>> getTechniquesByRegion() {
        Map<String, List<String>> result = new LinkedHashMap<>();

        ontology.listRegions().forEach(region -> result.put(
                region.localName(),
                ontology.listTechniquesUsedByRegion(region.localName()).stream()
                        .map(technique -> technique.localName())
                        .toList()
        ));

        return result;
    }

    public Map<String, List<String>> getOrnamentsByType() {
        Map<String, List<String>> result = new LinkedHashMap<>();
        ontology.listOrnamentTypes().forEach(type -> result.put(
                type.localName(),
                localNames(ontology.listOrnaments().stream()
                        .filter(ornament -> ontology.isOrnamentOfType(ornament.localName(), type.localName()))
                        .toList())
        ));
        return result;
    }

    public Map<String, List<String>> getTechniquesByType() {
        Map<String, List<String>> result = new LinkedHashMap<>();
        ontology.listTechniqueTypes().forEach(type -> result.put(
                type.localName(),
                localNames(ontology.listTechniquesOfType(type.localName()))
        ));
        return result;
    }

    private List<String> localNames(List<fmi.ethnowear.ontology.model.OntologyResource> resources) {
        return resources.stream().map(resource -> resource.localName()).toList();
    }

    public List<ReferenceItemDto> getRegions(String languageTag) {
        OntologyLanguage language = OntologyLanguage.fromTag(languageTag);
        return toDtos(language, ontology::listLocalizedRegions);
    }

    public List<ReferenceItemDto> getRegionGroups(String languageTag) {
        OntologyLanguage language = OntologyLanguage.fromTag(languageTag);
        return toDtos(language, ontology::listLocalizedRegionGroups);
    }

    public List<ReferenceItemDto> getOrnaments(String languageTag) {
        OntologyLanguage language = OntologyLanguage.fromTag(languageTag);
        return toDtos(language, ontology::listLocalizedOrnaments);
    }

    public List<ReferenceItemDto> getOrnamentTypes(String languageTag) {
        OntologyLanguage language = OntologyLanguage.fromTag(languageTag);
        return toDtos(language, ontology::listLocalizedOrnamentTypes);
    }

    public List<ReferenceItemDto> getColors(String languageTag) {
        OntologyLanguage language = OntologyLanguage.fromTag(languageTag);
        return toDtos(language, ontology::listLocalizedColors);
    }

    public List<ReferenceItemDto> getTechniques(String languageTag) {
        OntologyLanguage language = OntologyLanguage.fromTag(languageTag);
        return toDtos(language, ontology::listLocalizedTechniques);
    }

    public List<ReferenceItemDto> getTechniqueTypes(String languageTag) {
        OntologyLanguage language = OntologyLanguage.fromTag(languageTag);
        return toDtos(language, ontology::listLocalizedTechniqueTypes);
    }

    public List<ReferenceItemDto> getMotifs(String languageTag) {
        OntologyLanguage language = OntologyLanguage.fromTag(languageTag);
        return toDtos(language, ontology::listLocalizedMotifs);
    }

    public List<ReferenceItemDto> getRegionalEmbroideryTypes(String languageTag) {
        OntologyLanguage language = OntologyLanguage.fromTag(languageTag);
        return toDtos(language, ontology::listLocalizedRegionalEmbroideryTypes);
    }

    @NonNull
    @Unmodifiable
    private List<ReferenceItemDto> toDtos(
            @NonNull OntologyLanguage language,
            @NonNull Function<OntologyLanguage, List<LocalizedOntologyResource>> localizedResources
    ) {
        List<LocalizedOntologyResource> resources = localizedResources.apply(language);
        Map<String, LocalizedOntologyResource> bgResources = byLocalName(localizedResources.apply(OntologyLanguage.BG));
        Map<String, LocalizedOntologyResource> enResources = byLocalName(localizedResources.apply(OntologyLanguage.EN));

        return resources.stream()
                .map(resource -> toDto(resource, bgResources.get(resource.localName()), enResources.get(resource.localName())))
                .toList();
    }

    @NonNull
    @Unmodifiable
    private Map<String, LocalizedOntologyResource> byLocalName(@NonNull List<LocalizedOntologyResource> resources) {
        Map<String, LocalizedOntologyResource> result = new LinkedHashMap<>();
        resources.forEach(resource -> result.put(resource.localName(), resource));
        return result;
    }

    @NonNull
    @Unmodifiable
    private ReferenceItemDto toDto(
            @NonNull LocalizedOntologyResource resource,
            LocalizedOntologyResource bgResource,
            LocalizedOntologyResource enResource
    ) {
        Map<String, String> labels = new LinkedHashMap<>();
        Map<String, List<String>> altLabelsByLanguage = new LinkedHashMap<>();
        Map<String, String> comments = new LinkedHashMap<>();

        addLocalizedValues(labels, altLabelsByLanguage, comments, "bg", bgResource);
        addLocalizedValues(labels, altLabelsByLanguage, comments, "en", enResource);

        return new ReferenceItemDto(
                resource.iri(),
                resource.localName(),
                resource.label(),
                resource.altLabels(),
                resource.comment(),
                labels,
                altLabelsByLanguage,
                comments
        );
    }

    private void addLocalizedValues(
            Map<String, String> labels,
            Map<String, List<String>> altLabelsByLanguage,
            Map<String, String> comments,
            String language,
            LocalizedOntologyResource resource
    ) {
        if (resource == null) {
            return;
        }

        labels.put(language, resource.label());
        altLabelsByLanguage.put(language, resource.altLabels());

        if (resource.comment() != null) {
            comments.put(language, resource.comment());
        }
    }

}
