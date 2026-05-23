package fmi.ethnowear.ontology.embroidery;

import java.util.List;
import java.util.Optional;

import fmi.ethnowear.ontology.LocalizedOntologyResource;
import fmi.ethnowear.ontology.OntologyLanguage;
import fmi.ethnowear.ontology.OntologyResource;
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
 }
