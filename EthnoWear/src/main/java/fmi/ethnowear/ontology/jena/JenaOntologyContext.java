package fmi.ethnowear.ontology.jena;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import fmi.ethnowear.ontology.model.LocalizedOntologyResource;
import fmi.ethnowear.ontology.model.OntologyLanguage;
import lombok.Getter;

import lombok.NonNull;
import org.apache.jena.ontology.Individual;
import org.apache.jena.ontology.OntClass;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.rdf.model.*;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.RDFS;

import fmi.ethnowear.ontology.model.OntologyResource;

public abstract class JenaOntologyContext {

    private static final String SKOS_NS = "http://www.w3.org/2004/02/skos/core#";
    private static final Property SKOS_ALT_LABEL = ResourceFactory.createProperty(SKOS_NS, "altLabel");

    @Getter
    private final String namespace;
    @Getter
    private final OntModel model;

    public JenaOntologyContext(Path ontologyPath, String namespace) {
        this.namespace = namespace;
        this.model = loadOntology(ontologyPath);
    }

    protected List<OntologyResource> classes(){
        return model.listClasses()
                .filterKeep(r -> r.getURI() != null)
                .mapWith(this::toResource)
                .toList()
                .stream()
                .sorted(Comparator.comparing(OntologyResource::localName))
                .toList();
    }

    protected List<OntologyResource> individuals(){
        return model.listIndividuals()
                .filterKeep(r -> r.getURI() != null)
                .mapWith(this::toResource)
                .toList()
                .stream()
                .sorted(Comparator.comparing(OntologyResource::localName))
                .toList();
    }

    protected List<OntologyResource> individualsOfClass(String classLocalName){
        OntClass ontClass = model.getOntClass(toUri(classLocalName));
        if(ontClass == null)
            return List.of();

        return ontClass.listInstances()
                .filterKeep(r -> r.getURI() != null)
                .mapWith(this::toResource)
                .toList()
                .stream()
                .sorted(Comparator.comparing(OntologyResource::localName))
                .toList();
    }

    protected List<OntologyResource> propertyResource(String subjectLocalName, String propertyLocalName){
        Resource subject = model.getResource(toUri(subjectLocalName));
        Property property = model.getProperty(toUri(propertyLocalName));

        return model.listObjectsOfProperty(subject, property)
                .filterKeep(RDFNode::isResource)
                .mapWith(RDFNode::asResource)
                .filterKeep(r -> r.getURI() != null)
                .mapWith(this::toResource)
                .toList()
                .stream()
                .sorted(Comparator.comparing(OntologyResource::localName))
                .toList();
    }

    protected List<OntologyResource> subclassesOf(String classLocalName, boolean direct){
        OntClass ontClass = model.getOntClass(toUri(classLocalName));
        if(ontClass == null)
            return List.of();

        return ontClass.listSubClasses(direct)
                .filterKeep(r -> r.getURI() != null)
                .mapWith(this::toResource)
                .toList()
                .stream()
                .sorted(Comparator.comparing(OntologyResource::localName))
                .toList();
    }

    protected List<OntologyResource> typesOfIndividual(String individualLocalName, boolean direct){
        Individual individual = model.getIndividual(toUri(individualLocalName));
        if(individual == null)
            return List.of();

        return individual.listRDFTypes(direct)
                .filterKeep(r -> r.getURI() != null)
                .mapWith(this::toResource)
                .toList()
                .stream()
                .sorted(Comparator.comparing(OntologyResource::localName))
                .toList();
    }

    protected Optional<OntologyResource> findResource(String localName){
        Resource resource = model.getResource(toUri(localName));
        if(resource == null || resource.getURI() == null)
            return Optional.empty();

        return Optional.of(toResource(resource));
    }

    protected Optional<OntologyResource> findClassResource(String localName){
        OntClass ontClass = model.getOntClass(toUri(localName));
        if(ontClass == null || ontClass.getURI() == null)
            return Optional.empty();

        return Optional.of(toResource(ontClass));
    }

    protected Optional<OntologyResource> findIndividualResource(String localName){
        Individual individual = model.getIndividual(toUri(localName));
        if(individual == null || individual.getURI() == null)
            return Optional.empty();

        return Optional.of(toResource(individual));
    }

    protected boolean isSubclassOf(String childClassLocalName, String parentClassLocalName){
        OntClass child = model.getOntClass(toUri(childClassLocalName));
        OntClass parent = model.getOntClass(toUri(parentClassLocalName));

        return child != null && parent != null && child.hasSuperClass(parent);
    }

    protected boolean isIndividualOfClass(String individualLocalName, String classLocalName){
        Individual individual = model.getIndividual(toUri(individualLocalName));
        OntClass ontClass = model.getOntClass(toUri(classLocalName));

        return individual != null && ontClass != null && individual.hasOntClass(ontClass, false);
    }

    protected Optional<OntologyResource> hasValueRestriction(String classLocalName, String propertyLocalName){
        OntClass ontClass = model.getOntClass(toUri(classLocalName));
        Property targetProperty = model.getProperty(toUri(propertyLocalName));
        if(ontClass == null || targetProperty == null)
            return Optional.empty();

        StmtIterator superClasses = model.listStatements(ontClass, RDFS.subClassOf, (RDFNode) null);
        while (superClasses.hasNext()){
            RDFNode node = superClasses.nextStatement().getObject();
            if(!node.isResource())
                continue;

            Resource restriction = node.asResource();
            if(!model.contains(restriction, OWL.onProperty, targetProperty))
                continue;

            StmtIterator values = model.listStatements(restriction, OWL.hasValue, (RDFNode) null);
            while (values.hasNext()){
                RDFNode value = values.nextStatement().getObject();
                if(value.isResource() && value.asResource().getURI() != null)
                    return Optional.of(toResource(value.asResource()));
            }
        }
        return Optional.empty();
    }

    protected LocalizedOntologyResource toLocalizedResource(@NonNull Resource resource, OntologyLanguage language) {
        return new LocalizedOntologyResource(
                resource.getURI(),
                resource.getLocalName(),
                preferredLabel(resource, language).orElse(resource.getLocalName()),
                altLabels(resource, language),
                comment(resource, language).orElse(null),
                language
        );
    }

    protected List<LocalizedOntologyResource> toLocalizedResources(@NonNull List<OntologyResource> resources, OntologyLanguage language) {
        return resources.stream()
                .map(resource -> model.getResource(resource.iri()))
                .map(resource -> toLocalizedResource(resource, language))
                .toList();
    }

    protected Optional<String> preferredLabel(Resource resource, OntologyLanguage language) {
        return literalValues(resource, RDFS.label, language)
                .stream()
                .findFirst();
    }

    protected List<String> altLabels(Resource resource, OntologyLanguage language) {
        return literalValues(resource, SKOS_ALT_LABEL, language);
    }

    protected Optional<String> comment(Resource resource, OntologyLanguage language) {
        return literalValues(resource, RDFS.comment, language)
                .stream()
                .findFirst();
    }

    protected List<String> literalValues(Resource resource, Property property, OntologyLanguage language) {
        List<String> values = new ArrayList<>();

        StmtIterator statements = model.listStatements(resource, property, (RDFNode) null);
        while (statements.hasNext()) {
            RDFNode node = statements.nextStatement().getObject();

            if (node instanceof Literal literal
                    && language.tag().equalsIgnoreCase(literal.getLanguage())) {
                values.add(literal.getString());
            }
        }

        return values;
    }

    protected boolean matchesLabelOrAltLabel(@NonNull Resource resource, String text, OntologyLanguage language) {
        String normalizedText = normalize(text);

        if (resource.getLocalName() != null
                && normalize(resource.getLocalName()).equals(normalizedText)) {
            return true;
        }

        boolean labelMatches = literalValues(resource, RDFS.label, language)
                .stream()
                .map(this::normalize)
                .anyMatch(normalizedText::equals);

        if (labelMatches) {
            return true;
        }

        return literalValues(resource, SKOS_ALT_LABEL, language)
                .stream()
                .map(this::normalize)
                .anyMatch(normalizedText::equals);
    }

    protected String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }


    protected String toUri(@NonNull String value) {
        if (value.startsWith("http://") || value.startsWith("https://")) {
            return value;
        }
        String localName = value.startsWith("#") ? value.substring(1) : value;
        return namespace + localName;
    }

    protected OntologyResource toResource(@NonNull Resource resource) {
        return new OntologyResource(
                resource.getURI(),
                resource.getLocalName(),
                labelFor(resource).orElse(null)
        );
    }

    private Optional<String> labelFor(Resource resource) {
        StmtIterator labels = model.listStatements(resource, RDFS.label, (RDFNode) null);
        while (labels.hasNext()) {
            RDFNode value = labels.nextStatement().getObject();
            if (value instanceof Literal literal) {
                return Optional.of(literal.getString());
            }
        }

        return Optional.empty();
    }

    @NonNull
    private OntModel loadOntology(@NonNull Path path) {
        File ontologyFile = path.toFile();

        if (!ontologyFile.isFile()) {
            throw new IllegalStateException("Ontology file not found: " + ontologyFile.getPath());
        }

        OntModel loadedModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_MEM_RULE_INF);
        try (InputStream input = new FileInputStream(ontologyFile)) {
            loadedModel.read(input, namespace);
            return loadedModel;
        } catch (Exception exception) {
            throw new IllegalStateException("Could not load ontology file with Jena: " + ontologyFile.getPath(),
                    exception);
        }
    }
}
