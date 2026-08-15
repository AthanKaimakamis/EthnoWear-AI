package fmi.ethnowear.ontology.embroidery;

import fmi.ethnowear.ontology.embroidery.model.LocalizedRegionGroupProfile;
import fmi.ethnowear.ontology.embroidery.model.LocalizedRegionProfile;
import fmi.ethnowear.ontology.embroidery.model.RegionProfile;
import fmi.ethnowear.ontology.model.LocalizedOntologyResource;
import fmi.ethnowear.ontology.enums.OntologyLanguage;
import fmi.ethnowear.ontology.model.OntologyResource;

import java.util.List;
import java.util.Optional;

public interface EmbroideryOntologyClient {

    // Regions

    List<OntologyResource> listRegions();

    List<LocalizedOntologyResource> listLocalizedRegions(OntologyLanguage language);

    Optional<OntologyResource> findRegionByName(String regionNameOrLocalName, OntologyLanguage language);

    Optional<RegionProfile> describeRegion(String regionLocalName);

    Optional<LocalizedRegionProfile> describeLocalizedRegion(String regionNameOrLocalName, OntologyLanguage language);

    List<OntologyResource> listOrnamentsUsedByRegion(String regionLocalName);

    List<OntologyResource> listColorsUsedByRegion(String regionLocalName);

    List<OntologyResource> listTechniquesUsedByRegion(String regionLocalName);

    List<LocalizedOntologyResource> listLocalizedOrnamentsUsedByRegion(String regionLocalName, OntologyLanguage language);

    // Region groups

    List<OntologyResource> listRegionGroups();

    List<LocalizedOntologyResource> listLocalizedRegionGroups(OntologyLanguage language);

    List<OntologyResource> listRegionsInGroup(String regionLocalName);

    Optional<OntologyResource> findRegionGroupForRegion(String regionLocalName);

    Optional<OntologyResource> findRegionGroupByName(String nameOrLocalname, OntologyLanguage language);

    Optional<LocalizedRegionGroupProfile> describeRegionGroup(String groupNameOrLocalName, OntologyLanguage language);

    // Ornaments

    List<OntologyResource> listOrnaments();

    List<LocalizedOntologyResource> listLocalizedOrnaments(OntologyLanguage language);

    List<OntologyResource> listOrnamentTypes();

    List<LocalizedOntologyResource> listLocalizedOrnamentTypes(OntologyLanguage language);

    List<OntologyResource> listGeometricOrnaments();

    List<OntologyResource> listPlantOrnaments();

    List<OntologyResource> listAnimalOrnaments();

    List<OntologyResource> listHumanOrnaments();

    List<OntologyResource> listSymbolicOrnaments();

    List<OntologyResource> listTypesOfOrnament(String ornamentLocalName);

    boolean isOrnamentOfType(String ornamentLocalName, String ornamentTypeLocalName);

    // Colors

    List<OntologyResource> listColors();

    List<LocalizedOntologyResource> listLocalizedColors(OntologyLanguage language);

    // Techniques

    List<OntologyResource> listTechniques();

    List<LocalizedOntologyResource> listLocalizedTechniques(OntologyLanguage language);

    List<OntologyResource> listTechniqueTypes();

    List<LocalizedOntologyResource> listLocalizedTechniqueTypes(OntologyLanguage language);

    List<OntologyResource> listTechniquesOfType(String techniqueTypeLocalName);

    List<OntologyResource> listTypesOfTechnique(String techniqueLocalName);

    // Motifs

    List<OntologyResource> listMotifs();

    List<LocalizedOntologyResource> listLocalizedMotifs(OntologyLanguage language);

    List<OntologyResource> listMotifsOfEmbroidery(String embroideryLocalName);

    List<OntologyResource> listOrnamentOfMotif(String motifLocalName);

    List<OntologyResource> listColorsOfMotif(String motifLocalName);

    List<OntologyResource> listTechniquesOfMotif(String motifLocalName);

    // Regional embroidery types

    List<OntologyResource> listRegionalEmbroideryTypes();

    List<LocalizedOntologyResource> listLocalizedRegionalEmbroideryTypes(OntologyLanguage language);

    Optional<OntologyResource> findRegionalEmbroideryByName(String nameOrLocalName, OntologyLanguage language);

    Optional<OntologyResource> findRegionForRegionalEmbroidery(String regionalEmbroideryClassName);

    Optional<LocalizedOntologyResource> findLocalizedRegionForRegionalEmbroidery(String regionalEmbroideryClassLocalName, OntologyLanguage language);

    Optional<LocalizedOntologyResource> findLocalizedRegionalEmbroideryByName(String nameOrLocalName, OntologyLanguage language);

    List<OntologyResource> listRegionsOfMotif(String motifLocalName);

    List<OntologyResource> listOrnamentsOfEmbroidery(String embroideryLocalName);

    List<OntologyResource> listColorsOfEmbroidery(String embroideryLocalName);

    List<OntologyResource> listTechniquesOfEmbroidery(String embroideryLocalName);

    List<OntologyResource> listRegionalEmbroideriesForRegion(String regionLocalName);

    List<OntologyResource> listRegionsUsingOrnament(String ornamentLocalName);

    List<OntologyResource> listMotifsUsingOrnament(String ornamentLocalName);

    List<OntologyResource> listRegionalEmbroideriesUsingOrnament(String ornamentLocalName);

    List<OntologyResource> listRegionsUsingTechnique(String techniqueLocalName);

    List<OntologyResource> listMotifsUsingTechnique(String techniqueLocalName);

    List<OntologyResource> listRegionalEmbroideriesUsingTechnique(String techniqueLocalName);

    List<OntologyResource> listRegionsUsingColor(String colorLocalName);

    List<OntologyResource> listMotifsUsingColor(String colorLocalName);

    List<OntologyResource> listRegionalEmbroideriesUsingColor(String colorLocalName);

    List<OntologyResource> listRegionalEmbroideriesUsingMotif(String motifLocalName);
}
