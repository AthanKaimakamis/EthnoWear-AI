package fmi.ethnowear.ontology.embroidery;


import fmi.ethnowear.ontology.LocalizedOntologyResource;
import fmi.ethnowear.ontology.OntologyLanguage;
import fmi.ethnowear.ontology.OntologyResource;

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
}
