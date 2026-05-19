package fmi.ethnowear.ontology;

import java.io.File;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLIndividual;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.search.EntitySearcher;
import org.springframework.stereotype.Service;

import fmi.ethnowear.config.OntologyProperties;

@Service
public class EthnowearOntology implements EthnoWearOntologyClient {

    private final OntologyProperties properties;
    private final OWLOntology ontology;
    private final OWLDataFactory dataFactory;

    public EthnowearOntology(OntologyProperties properties) {
        this.properties = properties;

        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
        this.dataFactory = manager.getOWLDataFactory();
        this.ontology = loadOntology(manager, properties);
    }

    @Override
    public String namespace() {
        return properties.getNamespace();
    }

    @Override
    public List<OntologyResource> listClasses() {
        return ontology.classesInSignature()
                .map(this::toResource)
                .sorted(Comparator.comparing(OntologyResource::localName))
                .toList();
    }

    @Override
    public List<OntologyResource> listIndividuals() {
        return ontology.individualsInSignature()
                .map(this::toResource)
                .sorted(Comparator.comparing(OntologyResource::localName))
                .toList();
    }

    @Override
    public List<OntologyResource> listIndividualsOfClass(String classLocalName) {
        OWLClass owlClass = dataFactory.getOWLClass(toIri(classLocalName));

        return EntitySearcher.getIndividuals(owlClass, ontology)
                .filter(OWLIndividual::isNamed)
                .map(OWLIndividual::asOWLNamedIndividual)
                .map(this::toResource)
                .sorted(Comparator.comparing(OntologyResource::localName))
                .toList();
    }

    @Override
    public List<OntologyResource> listObjectPropertyValues(String subjectLocalName, String propertyLocalName) {
        var subject = dataFactory.getOWLNamedIndividual(toIri(subjectLocalName));
        OWLObjectProperty property = dataFactory.getOWLObjectProperty(toIri(propertyLocalName));

        return EntitySearcher.getObjectPropertyValues(subject, property, ontology)
                .filter(OWLIndividual::isNamed)
                .map(OWLIndividual::asOWLNamedIndividual)
                .map(this::toResource)
                .sorted(Comparator.comparing(OntologyResource::localName))
                .toList();
    }

    @Override
    public Optional<OntologyResource> findClass(String localName) {
        IRI iri = toIri(localName);

        return ontology.classesInSignature()
                .filter(owlClass -> owlClass.getIRI().equals(iri))
                .findFirst()
                .map(this::toResource);
    }

    @Override
    public Optional<OntologyResource> findIndividual(String localName) {
        IRI iri = toIri(localName);

        return ontology.individualsInSignature()
                .filter(individual -> individual.getIRI().equals(iri))
                .findFirst()
                .map(this::toResource);
    }

    private OWLOntology loadOntology(OWLOntologyManager manager, OntologyProperties properties) {
        File ontologyFile = properties.getPath().toFile();

        if (!ontologyFile.isFile()) {
            throw new IllegalStateException("Ontology file not found: " + ontologyFile.getPath());
        }

        try {
            return manager.loadOntologyFromOntologyDocument(ontologyFile);
        } catch (OWLOntologyCreationException exception) {
            throw new IllegalStateException("Could not load ontology file: " + ontologyFile.getPath(), exception);
        }
    }

    private IRI toIri(String value) {
        if (value.startsWith("http://") || value.startsWith("https://")) {
            return IRI.create(value);
        }

        String localName = value.startsWith("#") ? value.substring(1) : value;
        return IRI.create(properties.getNamespace() + localName);
    }

    private OntologyResource toResource(OWLClass owlClass) {
        IRI iri = owlClass.getIRI();
        return new OntologyResource(iri.toString(), iri.getShortForm(), labelFor(owlClass).orElse(null));
    }

    private OntologyResource toResource(org.semanticweb.owlapi.model.OWLNamedIndividual individual) {
        IRI iri = individual.getIRI();
        return new OntologyResource(iri.toString(), iri.getShortForm(), labelFor(individual).orElse(null));
    }

    private Optional<String> labelFor(org.semanticweb.owlapi.model.OWLEntity entity) {
        return EntitySearcher.getAnnotations(entity, ontology, dataFactory.getRDFSLabel())
                .map(annotation -> annotation.getValue())
                .filter(OWLLiteral.class::isInstance)
                .map(OWLLiteral.class::cast)
                .map(OWLLiteral::getLiteral)
                .findFirst();
    }
}
