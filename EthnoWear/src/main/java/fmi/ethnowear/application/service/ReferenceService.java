package fmi.ethnowear.application.service;

import fmi.ethnowear.api.dto.reference.ReferenceItemDto;
import fmi.ethnowear.api.dto.reference.ReferenceResponse;
import fmi.ethnowear.ontology.embroidery.EmbroideryOntologyClient;
import fmi.ethnowear.ontology.model.LocalizedOntologyResource;
import fmi.ethnowear.ontology.model.OntologyLanguage;
import org.jetbrains.annotations.Unmodifiable;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;

import java.util.List;


@Service
public class ReferenceService {

    private final EmbroideryOntologyClient ontology;


    public ReferenceService(EmbroideryOntologyClient ontology) {
        this.ontology = ontology;
    }

    public ReferenceResponse getFullReference(String languageTag) {
        OntologyLanguage language = parseLanguage(languageTag);

        return new ReferenceResponse(
                language.tag(),
                getRegions(language.tag()),
                getRegionGroups(language.tag()),
                getOrnaments(language.tag()),
                getColors(language.tag()),
                getTechniques(language.tag()),
                getMotifs(language.tag()),
                getRegionalEmbroideryTypes(language.tag())
        );
    }

    public List<ReferenceItemDto> getRegions(String languageTag) {
        OntologyLanguage language = parseLanguage(languageTag);
        return toDtos(ontology.listLocalizedRegions(language));
    }

    public List<ReferenceItemDto> getRegionGroups(String languageTag) {
        OntologyLanguage language = parseLanguage(languageTag);
        return toDtos(ontology.listLocalizedRegionGroups(language));
    }

    public List<ReferenceItemDto> getOrnaments(String languageTag) {
        OntologyLanguage language = parseLanguage(languageTag);
        return toDtos(ontology.listLocalizedOrnaments(language));
    }

    public List<ReferenceItemDto> getColors(String languageTag) {
        OntologyLanguage language = parseLanguage(languageTag);
        return toDtos(ontology.listLocalizedColors(language));
    }

    public List<ReferenceItemDto> getTechniques(String languageTag) {
        OntologyLanguage language = parseLanguage(languageTag);
        return toDtos(ontology.listLocalizedTechniques(language));
    }

    public List<ReferenceItemDto> getMotifs(String languageTag) {
        OntologyLanguage language = parseLanguage(languageTag);
        return toDtos(ontology.listLocalizedMotifs(language));
    }

    public List<ReferenceItemDto> getRegionalEmbroideryTypes(String languageTag) {
        OntologyLanguage language = parseLanguage(languageTag);
        return toDtos(ontology.listLocalizedRegionalEmbroideryTypes(language));
    }

    @NonNull
    @Unmodifiable
    private List<ReferenceItemDto> toDtos(@NonNull List<LocalizedOntologyResource> resources) {
        return resources.stream()
                .map(this::toDto)
                .toList();
    }

    @NonNull
    @Unmodifiable
    private ReferenceItemDto toDto(@NonNull LocalizedOntologyResource resource) {
        return new ReferenceItemDto(
                resource.iri(),
                resource.localName(),
                resource.label(),
                resource.altLabels(),
                resource.comment()
        );
    }

    private OntologyLanguage parseLanguage(String languageTag) {
        if(languageTag == null || languageTag.isBlank())
            return OntologyLanguage.BG;

        return switch (languageTag.trim().toLowerCase()) {
            case "en" -> OntologyLanguage.EN;
            case "bg" -> OntologyLanguage.BG;
            default -> OntologyLanguage.BG;
        };
    }
}
