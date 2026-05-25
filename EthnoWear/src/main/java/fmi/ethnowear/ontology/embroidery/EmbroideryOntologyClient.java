package fmi.ethnowear.ontology.embroidery;

import fmi.ethnowear.ontology.model.LocalizedOntologyResource;
import fmi.ethnowear.ontology.model.OntologyLanguage;
import fmi.ethnowear.ontology.model.OntologyResource;

import java.util.List;
import java.util.Optional;

public interface EmbroideryOntologyClient {
    List<OntologyResource> listOrnaments();

    List<OntologyResource> listGeometricOrnaments();

    List<OntologyResource> listPlantOrnaments();

    List<OntologyResource> listAnimalOrnaments();

    List<OntologyResource> listHumanOrnaments();

    List<OntologyResource> listSymbolicOrnaments();

    List<OntologyResource> listRegions();

    List<OntologyResource> listOrnamentsUsedByRegion(String regionLocalName);

    List<OntologyResource> listColorsUsedByRegion(String regionLocalName);

    List<OntologyResource> listTechniquesUsedByRegion(String regionLocalName);

    Optional<RegionProfile> describeRegion(String regionLocalName);

    List<OntologyResource> listTypesOfOrnament(String ornamentLocalName);

    boolean isOrnamentOfType(String ornamentLocalName, String ornamentTypeLocalName);

    List<LocalizedOntologyResource> listLocalizedOrnamentsUsedByRegion(String regionLocalName, OntologyLanguage language);

    Optional<LocalizedRegionProfile> describeLocalizedRegion(String regionNameOrLocalName, OntologyLanguage language);

    Optional<OntologyResource> findRegionByName(String regionNameOrLocalName, OntologyLanguage language);

    List<OntologyResource> listRegionalEmbroideryTypes();

    Optional<OntologyResource> findRegionalEmbroideryByName(String nameOrLocalName, OntologyLanguage language);

    Optional<OntologyResource> findRegionForRegionalEmbroidery(String regionalEmbroideryClassName);

    Optional<LocalizedOntologyResource> findLocalizedRegionForRegionalEmbroidery(String regionalEmbroideryClassLocalName,OntologyLanguage language);

    List<OntologyResource> listRegionGroups();

    List<OntologyResource> listRegionsInGroup(String regionLocalName);

    Optional<OntologyResource> findRegionGroupForRegion(String regionLocalName);

    Optional<LocalizedRegionGroupProfile> describeRegionGroup(String groupNameOrLocalName, OntologyLanguage language);

    Optional<OntologyResource> findRegionGroupByName(String nameOrLocalname, OntologyLanguage language);

    List<OntologyResource> listMotifs();

    List<OntologyResource> listMotifsOfEmbroidery(String embroideryLocalName);

    List<OntologyResource> listOrnamentOfMotif(String motifLocalName);

    List<OntologyResource> listColorsOfMotif(String motifLocalName);

    List<OntologyResource> listTechniquesOfMotif(String motifLocalName);

    Optional<LocalizedOntologyResource> findLocalizedRegionalEmbroideryByName(String nameOrLocalName, OntologyLanguage language);

}
