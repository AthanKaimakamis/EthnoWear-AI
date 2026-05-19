package fmi.ethnowear.ontology;

import java.util.List;
import java.util.Optional;

public interface EthnoWearOntologyClient {

    String namespace();

    List<OntologyResource> listClasses();

    List<OntologyResource> listIndividuals();

    List<OntologyResource> listIndividualsOfClass(String classLocalName);

    List<OntologyResource> listObjectPropertyValues(String subjectLocalName, String propertyLocalName);

    Optional<OntologyResource> findClass(String localName);

    Optional<OntologyResource> findIndividual(String localName);
}
