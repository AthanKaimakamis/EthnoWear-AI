package fmi.ethnowear.ontology.embroidery;

import java.util.List;
import java.util.Optional;

import fmi.ethnowear.ontology.model.LocalizedOntologyResource;
import fmi.ethnowear.ontology.model.OntologyLanguage;
import fmi.ethnowear.ontology.model.OntologyResource;
import fmi.ethnowear.ontology.jena.JenaOntologyContext;
import org.springframework.stereotype.Service;

import fmi.ethnowear.config.OntologyProperties;

@Service
public class EmbroideryOntology extends JenaOntologyContext implements EmbroideryOntologyClient {

    public EmbroideryOntology(OntologyProperties properties) {
        super(properties.getPath(), properties.getNamespace());
    }

    @Override
    public List<OntologyResource> listOrnaments() {
        return individualsOfClass("Ornament");
    }

    @Override
    public List<OntologyResource> listGeometricOrnaments() {
        return individualsOfClass("GeometricOrnament");
    }

    @Override
    public List<OntologyResource> listPlantOrnaments() {
        return individualsOfClass("PlantOrnament");
    }

    @Override
    public List<OntologyResource> listAnimalOrnaments() {
        return individualsOfClass("AnimalOrnament");
    }

    @Override
    public List<OntologyResource> listHumanOrnaments() {
        return individualsOfClass("HumanOrnament");
    }

    @Override
    public List<OntologyResource> listSymbolicOrnaments() {
        return individualsOfClass("SymbolicOrnament");
    }

    @Override
    public List<OntologyResource> listRegions() {
        return individualsOfClass("Region");
    }

    @Override
    public List<OntologyResource> listOrnamentsUsedByRegion(String regionLocalName) {
        return propertyResource(regionLocalName, "regionUsesOrnament");
    }

    @Override
    public List<OntologyResource> listColorsUsedByRegion(String regionLocalName) {
        return propertyResource(regionLocalName, "regionUsesColor");
    }

    @Override
    public List<OntologyResource> listTechniquesUsedByRegion(String regionLocalName) {
        return propertyResource(regionLocalName, "regionUsesTechnique");
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
    public List<OntologyResource> listTypesOfOrnament(String ornamentLocalName) {
        return typesOfIndividual(ornamentLocalName, false);
    }

    @Override
    public boolean isOrnamentOfType(String ornamentLocalName, String ornamentTypeLocalName) {
        return isIndividualOfClass(ornamentLocalName, ornamentTypeLocalName);
    }

    @Override
    public List<LocalizedOntologyResource> listLocalizedOrnamentsUsedByRegion(String regionLocalName, OntologyLanguage language) {
        return toLocalizedResources(
                listOrnamentsUsedByRegion(regionLocalName),
                language
        );
    }

    @Override
    public Optional<LocalizedRegionProfile> describeLocalizedRegion(String regionNameOrLocalName, OntologyLanguage language) {
        return findRegionByName(regionNameOrLocalName, language)
                .map(region -> new LocalizedRegionProfile(
                        toLocalizedResource(getModel().getResource(region.iri()), language),
                        toLocalizedResources(listOrnamentsUsedByRegion(region.localName()), language),
                        toLocalizedResources(listColorsUsedByRegion(region.localName()), language),
                        toLocalizedResources(listTechniquesUsedByRegion(region.localName()), language)
                ));
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
    public List<OntologyResource> listRegionalEmbroideryTypes(){
        return subclassesOf("RegionalEmbroidery", true);
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
    public Optional<OntologyResource> findRegionForRegionalEmbroidery(String regionalEmbroideryClassName){
        return hasValueRestriction(regionalEmbroideryClassName, "hasRegion");
    }

    @Override
    public Optional<LocalizedOntologyResource> findLocalizedRegionForRegionalEmbroidery(String regionalEmbroideryClassLocalName, OntologyLanguage language) {
        return findRegionForRegionalEmbroidery(regionalEmbroideryClassLocalName)
                .map(region -> toLocalizedResource(
                        getModel().getResource(region.iri()),
                        language));
    }

    @Override
    public List<OntologyResource> listRegionGroups() {
        return individualsOfClass("RegionGroup");
    }

    @Override
    public List<OntologyResource> listRegionsInGroup(String regionGroupLocalName) {
        List<OntologyResource> inferredMember = propertyResource(regionGroupLocalName, "hasRegionMember");
        if(!inferredMember.isEmpty())
            return inferredMember;

        return listRegions().stream()
                .filter(region -> propertyResource(region.localName(), "belongsToRegionGroup")
                        .stream()
                        .anyMatch(group -> group.localName().equals(regionGroupLocalName)))
                .toList();
    }

    @Override
    public Optional<OntologyResource> findRegionGroupForRegion(String regionLocalName) {
        return propertyResource(regionLocalName, "belongsToRegionGroup")
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
                        toLocalizedResources(listRegionsInGroup(group.localName()), language)
                ));
    }

    @Override
    public List<OntologyResource> listMotifs() {
        return individualsOfClass("Motif");
    }

    @Override
    public List<OntologyResource> listMotifsOfEmbroidery(String embroideryLocalName) {
        return propertyResource(embroideryLocalName, "hasMotif");
    }

    @Override
    public List<OntologyResource> listOrnamentOfMotif(String motifLocalName) {
        return propertyResource(motifLocalName, "motifHasOrnament");
    }

    @Override
    public List<OntologyResource> listColorsOfMotif(String motifLocalName) {
        return propertyResource(motifLocalName, "motifHasColor");
    }

    @Override
    public List<OntologyResource> listTechniquesOfMotif(String motifLocalName) {
        return propertyResource(motifLocalName, "motifHasTechnique");
    }

    @Override
    public Optional<LocalizedOntologyResource> findLocalizedRegionalEmbroideryByName(String nameOrLocalName, OntologyLanguage language){
        return  findRegionalEmbroideryByName(nameOrLocalName, language)
                .map(type -> toLocalizedResource(
                        getModel().getResource(type.iri()),
                        language));
    }
}
