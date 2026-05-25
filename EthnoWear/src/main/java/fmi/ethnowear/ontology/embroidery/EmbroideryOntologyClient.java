package fmi.ethnowear.ontology.embroidery;

import fmi.ethnowear.ontology.model.LocalizedOntologyResource;
import fmi.ethnowear.ontology.model.OntologyLanguage;
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
}
