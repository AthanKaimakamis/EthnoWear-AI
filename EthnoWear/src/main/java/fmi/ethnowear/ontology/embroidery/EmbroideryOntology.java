package fmi.ethnowear.ontology.embroidery;

import fmi.ethnowear.config.OntologyProperties;
import fmi.ethnowear.ontology.OntologyTerms;
import fmi.ethnowear.ontology.jena.JenaOntologyContext;
import fmi.ethnowear.ontology.model.LocalizedOntologyResource;
import fmi.ethnowear.ontology.model.OntologyLanguage;
import fmi.ethnowear.ontology.model.OntologyResource;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class EmbroideryOntology extends JenaOntologyContext implements EmbroideryOntologyClient {

    public EmbroideryOntology(OntologyProperties properties) {
        super(properties.getPath(), properties.getNamespace());
    }

    // Regions

    @Override
    public List<OntologyResource> listRegions() {
        return individualsOfClass(OntologyTerms.Classes.REGION);
    }

    @Override
    public List<LocalizedOntologyResource> listLocalizedRegions(OntologyLanguage language) {
        return toLocalizedResourceList(listRegions(), language);
    }

    @Override
    public Optional<OntologyResource> findRegionByName(String regionNameOrLocalName, OntologyLanguage language) {
        return listRegions().stream()
                .filter(region -> matchesLabelOrAltLabel(
                        getModel().getResource(region.iri()),
                        regionNameOrLocalName,
                        language
                ))
                .findFirst();
    }

    @Override
    public Optional<RegionProfile> describeRegion(String regionLocalName) {
        return findIndividualResource(regionLocalName)
                .map(region -> new RegionProfile(
                        region,
                        listOrnamentsUsedByRegion(regionLocalName),
                        listColorsUsedByRegion(regionLocalName),
                        listTechniquesUsedByRegion(regionLocalName)
                ));
    }

    @Override
    public Optional<LocalizedRegionProfile> describeLocalizedRegion(String regionNameOrLocalName, OntologyLanguage language) {
        return findRegionByName(regionNameOrLocalName, language)
                .map(region -> new LocalizedRegionProfile(
                        toLocalizedResource(getModel().getResource(region.iri()), language),
                        toLocalizedResourceList(listOrnamentsUsedByRegion(region.localName()), language),
                        toLocalizedResourceList(listColorsUsedByRegion(region.localName()), language),
                        toLocalizedResourceList(listTechniquesUsedByRegion(region.localName()), language)
                ));
    }

    @Override
    public List<OntologyResource> listOrnamentsUsedByRegion(String regionLocalName) {
        return propertyResource(regionLocalName, OntologyTerms.ObjectProperties.REGION_USES_ORNAMENT);
    }

    @Override
    public List<OntologyResource> listColorsUsedByRegion(String regionLocalName) {
        return propertyResource(regionLocalName, OntologyTerms.ObjectProperties.REGION_USES_COLOR);
    }

    @Override
    public List<OntologyResource> listTechniquesUsedByRegion(String regionLocalName) {
        return propertyResource(regionLocalName, OntologyTerms.ObjectProperties.REGION_USES_TECHNIQUE);
    }

    @Override
    public List<LocalizedOntologyResource> listLocalizedOrnamentsUsedByRegion(String regionLocalName, OntologyLanguage language) {
        return toLocalizedResourceList(
                listOrnamentsUsedByRegion(regionLocalName),
                language
        );
    }

    // Region groups

    @Override
    public List<OntologyResource> listRegionGroups() {
        return individualsOfClass(OntologyTerms.Classes.REGION_GROUP);
    }

    @Override
    public List<LocalizedOntologyResource> listLocalizedRegionGroups(OntologyLanguage language) {
        return toLocalizedResourceList(listRegionGroups(), language);
    }

    @Override
    public List<OntologyResource> listRegionsInGroup(String regionGroupLocalName) {
        List<OntologyResource> inferredMember = propertyResource(regionGroupLocalName, OntologyTerms.ObjectProperties.HAS_REGION_MEMBER);
        if (!inferredMember.isEmpty()) {
            return inferredMember;
        }

        return listRegions().stream()
                .filter(region -> propertyResource(region.localName(), OntologyTerms.ObjectProperties.BELONGS_TO_REGION_GROUP)
                        .stream()
                        .anyMatch(group -> group.localName().equals(regionGroupLocalName)))
                .toList();
    }

    @Override
    public Optional<OntologyResource> findRegionGroupForRegion(String regionLocalName) {
        return propertyResource(regionLocalName, OntologyTerms.ObjectProperties.BELONGS_TO_REGION_GROUP)
                .stream()
                .findFirst();
    }

    @Override
    public Optional<OntologyResource> findRegionGroupByName(String nameOrLocalname, OntologyLanguage language) {
        return listRegionGroups().stream()
                .filter(group -> matchesLabelOrAltLabel(
                        getModel().getResource(group.iri()),
                        nameOrLocalname,
                        language))
                .findFirst();
    }

    @Override
    public Optional<LocalizedRegionGroupProfile> describeRegionGroup(String groupNameOrLocalName, OntologyLanguage language) {
        return findRegionGroupByName(groupNameOrLocalName, language)
                .map(group -> new LocalizedRegionGroupProfile(
                        toLocalizedResource(getModel().getResource(group.iri()), language),
                        toLocalizedResourceList(listRegionsInGroup(group.localName()), language)
                ));
    }

    // Ornaments

    @Override
    public List<OntologyResource> listOrnaments() {
        return individualsOfClass(OntologyTerms.Classes.ORNAMENT);
    }

    @Override
    public List<LocalizedOntologyResource> listLocalizedOrnaments(OntologyLanguage language) {
        return toLocalizedResourceList(listOrnaments(), language);
    }

    @Override
    public List<OntologyResource> listGeometricOrnaments() {
        return individualsOfClass(OntologyTerms.Classes.GEOMETRIC_ORNAMENT);
    }

    @Override
    public List<OntologyResource> listPlantOrnaments() {
        return individualsOfClass(OntologyTerms.Classes.PLANT_ORNAMENT);
    }

    @Override
    public List<OntologyResource> listAnimalOrnaments() {
        return individualsOfClass(OntologyTerms.Classes.ANIMAL_ORNAMENT);
    }

    @Override
    public List<OntologyResource> listHumanOrnaments() {
        return individualsOfClass(OntologyTerms.Classes.HUMAN_ORNAMENT);
    }

    @Override
    public List<OntologyResource> listSymbolicOrnaments() {
        return individualsOfClass(OntologyTerms.Classes.SYMBOLIC_ORNAMENT);
    }

    @Override
    public List<OntologyResource> listTypesOfOrnament(String ornamentLocalName) {
        return typesOfIndividual(ornamentLocalName, false);
    }

    @Override
    public boolean isOrnamentOfType(String ornamentLocalName, String ornamentTypeLocalName) {
        return isIndividualOfClass(ornamentLocalName, ornamentTypeLocalName);
    }

    // Colors

    @Override
    public List<OntologyResource> listColors() {
        return individualsOfClass(OntologyTerms.Classes.COLOR);
    }

    @Override
    public List<LocalizedOntologyResource> listLocalizedColors(OntologyLanguage language) {
        return toLocalizedResourceList(listColors(), language);
    }

    // Techniques

    @Override
    public List<OntologyResource> listTechniques() {
        return individualsOfClass(OntologyTerms.Classes.TECHNIQUE);
    }

    @Override
    public List<LocalizedOntologyResource> listLocalizedTechniques(OntologyLanguage language) {
        return toLocalizedResourceList(listTechniques(), language);
    }

    // Motifs

    @Override
    public List<OntologyResource> listMotifs() {
        return individualsOfClass(OntologyTerms.Classes.MOTIF);
    }

    @Override
    public List<LocalizedOntologyResource> listLocalizedMotifs(OntologyLanguage language) {
        return toLocalizedResourceList(listMotifs(), language);
    }

    @Override
    public List<OntologyResource> listMotifsOfEmbroidery(String embroideryLocalName) {
        return propertyResource(embroideryLocalName, OntologyTerms.ObjectProperties.HAS_MOTIF);
    }

    @Override
    public List<OntologyResource> listOrnamentOfMotif(String motifLocalName) {
        return propertyResource(motifLocalName, OntologyTerms.ObjectProperties.MOTIF_HAS_ORNAMENT);
    }

    @Override
    public List<OntologyResource> listColorsOfMotif(String motifLocalName) {
        return propertyResource(motifLocalName, OntologyTerms.ObjectProperties.MOTIF_HAS_COLOR);
    }

    @Override
    public List<OntologyResource> listTechniquesOfMotif(String motifLocalName) {
        return propertyResource(motifLocalName, OntologyTerms.ObjectProperties.MOTIF_HAS_TECHNIQUE);
    }

    // Regional embroidery types

    @Override
    public List<OntologyResource> listRegionalEmbroideryTypes() {
        return subclassesOf(OntologyTerms.Classes.REGIONAL_EMBROIDERY, true);
    }

    @Override
    public List<LocalizedOntologyResource> listLocalizedRegionalEmbroideryTypes(OntologyLanguage language) {
        return toLocalizedResourceList(listRegionalEmbroideryTypes(), language);
    }

    @Override
    public Optional<OntologyResource> findRegionalEmbroideryByName(String nameOrLocalName, OntologyLanguage language) {
        return listRegionalEmbroideryTypes().stream()
                .filter(type -> matchesLabelOrAltLabel(
                        getModel().getResource(type.iri()),
                        nameOrLocalName,
                        language
                ))
                .findFirst();
    }

    @Override
    public Optional<OntologyResource> findRegionForRegionalEmbroidery(String regionalEmbroideryClassName) {
        return hasValueRestriction(regionalEmbroideryClassName, OntologyTerms.ObjectProperties.HAS_REGION);
    }

    @Override
    public Optional<LocalizedOntologyResource> findLocalizedRegionForRegionalEmbroidery(String regionalEmbroideryClassLocalName, OntologyLanguage language) {
        return findRegionForRegionalEmbroidery(regionalEmbroideryClassLocalName)
                .map(region -> toLocalizedResource(
                        getModel().getResource(region.iri()),
                        language));
    }

    @Override
    public Optional<LocalizedOntologyResource> findLocalizedRegionalEmbroideryByName(String nameOrLocalName, OntologyLanguage language) {
        return findRegionalEmbroideryByName(nameOrLocalName, language)
                .map(type -> toLocalizedResource(
                        getModel().getResource(type.iri()),
                        language));
    }
}
