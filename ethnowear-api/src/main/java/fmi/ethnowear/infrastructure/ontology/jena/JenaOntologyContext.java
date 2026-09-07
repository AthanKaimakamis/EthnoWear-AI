package fmi.ethnowear.infrastructure.ontology.jena;

import fmi.ethnowear.domain.model.ontology.LocalizedOntologyResource;
import fmi.ethnowear.domain.model.ontology.OntologyLanguage;
import fmi.ethnowear.domain.model.ontology.OntologyResource;
import lombok.NonNull;
import org.apache.jena.ontology.Individual;
import org.apache.jena.ontology.OntClass;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.RDFS;
import org.jetbrains.annotations.Contract;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public abstract class JenaOntologyContext {

    private static final String SKOS_NS = "http://www.w3.org/2004/02/skos/core#";
    private static final Property SKOS_ALT_LABEL = ResourceFactory.createProperty(SKOS_NS, "altLabel");

    private final JenaOntologyStore store;

    protected JenaOntologyContext(JenaOntologyStore store) {
        this.store = store;
    }

    protected List<OntologyResource> classes() {
        return store.read(model -> model.listClasses()
                .filterKeep(resource -> resource.getURI() != null)
                .mapWith(resource -> toResource(model, resource))
                .toList()
                .stream()
                .sorted(Comparator.comparing(OntologyResource::localName))
                .toList());
    }

    protected List<OntologyResource> individuals() {
        return store.read(model -> model.listIndividuals()
                .filterKeep(resource -> resource.getURI() != null)
                .mapWith(resource -> toResource(model, resource))
                .toList()
                .stream()
                .sorted(Comparator.comparing(OntologyResource::localName))
                .toList());
    }

    protected List<OntologyResource> individualsOfClass(String classLocalName) {
        return store.read(model -> {
            OntClass ontClass = model.getOntClass(toUri(classLocalName));
            if (ontClass == null) {
                return List.of();
            }

            return ontClass.listInstances()
                    .filterKeep(resource -> resource.getURI() != null)
                    .mapWith(resource -> toResource(model, resource))
                    .toList()
                    .stream()
                    .sorted(Comparator.comparing(OntologyResource::localName))
                    .toList();
        });
    }

    protected List<OntologyResource> propertyResource(String subjectLocalName, String propertyLocalName) {
        return store.read(model -> {
            Resource subject = model.getResource(toUri(subjectLocalName));
            Property property = model.getProperty(toUri(propertyLocalName));

            return model.listObjectsOfProperty(subject, property)
                    .filterKeep(RDFNode::isResource)
                    .mapWith(RDFNode::asResource)
                    .filterKeep(resource -> resource.getURI() != null)
                    .mapWith(resource -> toResource(model, resource))
                    .toList()
                    .stream()
                    .sorted(Comparator.comparing(OntologyResource::localName))
                    .toList();
        });
    }

    protected List<OntologyResource> subclassesOf(String classLocalName, boolean direct) {
        return store.read(model -> {
            OntClass ontClass = model.getOntClass(toUri(classLocalName));
            if (ontClass == null) {
                return List.of();
            }

            return ontClass.listSubClasses(direct)
                    .filterKeep(resource -> resource.getURI() != null)
                    .mapWith(resource -> toResource(model, resource))
                    .toList()
                    .stream()
                    .sorted(Comparator.comparing(OntologyResource::localName))
                    .toList();
        });
    }

    protected List<OntologyResource> typesOfIndividual(String individualLocalName, boolean direct) {
        return store.read(model -> {
            Individual individual = model.getIndividual(toUri(individualLocalName));
            if (individual == null) {
                return List.of();
            }

            return individual.listRDFTypes(direct)
                    .filterKeep(resource -> resource.getURI() != null)
                    .mapWith(resource -> toResource(model, resource))
                    .toList()
                    .stream()
                    .sorted(Comparator.comparing(OntologyResource::localName))
                    .toList();
        });
    }

    protected Optional<OntologyResource> findResource(String localName) {
        return store.read(model -> {
            Resource resource = model.getResource(toUri(localName));
            if (!model.containsResource(resource)) {
                return Optional.empty();
            }
            return Optional.of(toResource(model, resource));
        });
    }

    protected Optional<OntologyResource> findClassResource(String localName) {
        return store.read(model -> {
            OntClass ontClass = model.getOntClass(toUri(localName));
            return ontClass == null || ontClass.getURI() == null
                    ? Optional.empty()
                    : Optional.of(toResource(model, ontClass));
        });
    }

    protected Optional<OntologyResource> findIndividualResource(String localName) {
        return store.read(model -> {
            Individual individual = model.getIndividual(toUri(localName));
            return individual == null || individual.getURI() == null
                    ? Optional.empty()
                    : Optional.of(toResource(model, individual));
        });
    }

    protected boolean isSubclassOf(String childClassLocalName, String parentClassLocalName) {
        return store.read(model -> {
            OntClass child = model.getOntClass(toUri(childClassLocalName));
            OntClass parent = model.getOntClass(toUri(parentClassLocalName));
            return child != null && parent != null && child.hasSuperClass(parent);
        });
    }

    protected boolean isIndividualOfClass(String individualLocalName, String classLocalName) {
        return store.read(model -> {
            Individual individual = model.getIndividual(toUri(individualLocalName));
            OntClass ontClass = model.getOntClass(toUri(classLocalName));
            return individual != null && ontClass != null && individual.hasOntClass(ontClass, false);
        });
    }

    protected Optional<OntologyResource> hasValueRestriction(String classLocalName, String propertyLocalName) {
        return hasValueRestrictions(classLocalName, propertyLocalName).stream().findFirst();
    }

    protected List<OntologyResource> hasValueRestrictions(String classLocalName, String propertyLocalName) {
        return store.read(model -> {
            OntClass ontClass = model.getOntClass(toUri(classLocalName));
            Property property = model.getProperty(toUri(propertyLocalName));
            if(ontClass == null || property == null)
                return List.of();

            return restrictionValues(model, ontClass, property).stream()
                    .map(resource -> toResource(model, resource))
                    .sorted(Comparator.comparing(OntologyResource::localName))
                    .toList();
        });
    }

    protected List<OntologyResource> individualsWithPropertyValue(
            String classLocalName,
            String propertyLocalName,
            String valueLocalName
    ) {
        return store.read(model -> {
            OntClass ontClass = model.getOntClass(toUri(classLocalName));
            Property property = model.getProperty(toUri(propertyLocalName));
            Resource value = model.getResource(toUri(valueLocalName));
            if(ontClass == null || property == null || !model.containsResource(value))
                return List.of();

            return ontClass.listInstances()
                    .filterKeep(resource -> resource.getURI() != null)
                    .filterKeep(resource -> model.contains(resource, property, value))
                    .mapWith(resource -> toResource(model, resource))
                    .toList()
                    .stream()
                    .sorted(Comparator.comparing(OntologyResource::localName))
                    .toList();
        });
    }

    protected List<OntologyResource> subclassesWithHasValueRestriction(
            String parentClassLocalName,
            String propertyLocalName,
            String valueLocalName
    ) {
        return store.read(model -> {
            OntClass parent = model.getOntClass(toUri(parentClassLocalName));
            Property property = model.getProperty(toUri(propertyLocalName));
            Resource expectedValue = model.getResource(toUri(valueLocalName));
            if(parent == null || property == null || !model.containsResource(expectedValue))
                return List.of();

            return parent.listSubClasses(true)
                    .filterKeep(ontologyClass -> ontologyClass.getURI() != null)
                    .filterKeep(ontologyClass -> restrictionValues(model, ontologyClass, property)
                            .stream()
                            .anyMatch(expectedValue::equals))
                    .mapWith(ontologyClass -> toResource(model, ontologyClass))
                    .toList()
                    .stream()
                    .sorted(Comparator.comparing(OntologyResource::localName))
                    .toList();
        });
    }

    private @NonNull List<Resource> restrictionValues(@org.jspecify.annotations.NonNull OntModel model, Resource ontologyClass, Property property) {
        List<Resource> result = new ArrayList<>();
        StmtIterator superClasses = model.listStatements(ontologyClass, RDFS.subClassOf, (RDFNode) null);

        while(superClasses.hasNext()) {
            RDFNode node = superClasses.nextStatement().getObject();
            if(!node.isResource())
                continue;

            Resource restriction = node.asResource();
            if(!model.contains(restriction, OWL.onProperty, property))
                continue;

            model.listObjectsOfProperty(restriction, OWL.hasValue)
                    .filterKeep(RDFNode::isURIResource)
                    .mapWith(RDFNode::asResource)
                    .forEachRemaining(result::add);
        }

        return result;
    }

    protected LocalizedOntologyResource toLocalizedResource(
            @NonNull OntologyResource resource,
            OntologyLanguage language
    ) {
        return store.read(model -> toLocalizedResource(model, model.getResource(resource.iri()), language));
    }

    protected List<LocalizedOntologyResource> toLocalizedResourceList(
            @NonNull List<OntologyResource> resources,
            OntologyLanguage language
    ) {
        return store.read(model -> resources.stream()
                .map(resource -> model.getResource(resource.iri()))
                .map(resource -> toLocalizedResource(model, resource, language))
                .toList());
    }

    protected boolean matchesLabelOrAltLabel(
            @NonNull OntologyResource resource,
            String text,
            OntologyLanguage language
    ) {
        return store.read(model -> matchesLabelOrAltLabel(model, model.getResource(resource.iri()), text, language));
    }

    protected String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    protected String toUri(@NonNull String value) {
        if (value.startsWith("http://") || value.startsWith("https://")) {
            return value;
        }
        String localName = value.startsWith("#") ? value.substring(1) : value;
        return store.uri(localName);
    }

    @Contract("_, _, _ -> new")
    private @org.jspecify.annotations.NonNull LocalizedOntologyResource toLocalizedResource(
            OntModel model,
            @org.jspecify.annotations.NonNull Resource resource,
            OntologyLanguage language
    ) {
        return new LocalizedOntologyResource(
                resource.getURI(),
                resource.getLocalName(),
                preferredLabel(model, resource, language).orElse(resource.getLocalName()),
                literalValues(model, resource, SKOS_ALT_LABEL, language),
                comment(model, resource, language).orElse(null),
                language
        );
    }

    private @org.jspecify.annotations.NonNull Optional<String> preferredLabel(OntModel model, Resource resource, OntologyLanguage language) {
        return literalValues(model, resource, RDFS.label, language).stream().findFirst();
    }

    private @org.jspecify.annotations.NonNull Optional<String> comment(OntModel model, Resource resource, OntologyLanguage language) {
        return literalValues(model, resource, RDFS.comment, language).stream().findFirst();
    }

    private @org.jspecify.annotations.NonNull List<String> literalValues(
            @org.jspecify.annotations.NonNull OntModel model,
            Resource resource,
            Property property,
            OntologyLanguage language
    ) {
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

    private boolean matchesLabelOrAltLabel(
            OntModel model,
            @org.jspecify.annotations.NonNull Resource resource,
            String text,
            OntologyLanguage language
    ) {
        String normalizedText = normalize(text);
        if (resource.getLocalName() != null
                && normalize(resource.getLocalName()).equals(normalizedText)) {
            return true;
        }

        boolean labelMatches = literalValues(model, resource, RDFS.label, language)
                .stream()
                .map(this::normalize)
                .anyMatch(normalizedText::equals);
        if (labelMatches) {
            return true;
        }

        return literalValues(model, resource, SKOS_ALT_LABEL, language)
                .stream()
                .map(this::normalize)
                .anyMatch(normalizedText::equals);
    }

    @Contract("_, _ -> new")
    private @org.jspecify.annotations.NonNull OntologyResource toResource(OntModel model, @org.jspecify.annotations.NonNull Resource resource) {
        return new OntologyResource(
                resource.getURI(),
                resource.getLocalName(),
                labelFor(model, resource).orElse(null)
        );
    }

    private Optional<String> labelFor(@org.jspecify.annotations.NonNull OntModel model, Resource resource) {
        StmtIterator labels = model.listStatements(resource, RDFS.label, (RDFNode) null);
        while (labels.hasNext()) {
            RDFNode value = labels.nextStatement().getObject();
            if (value instanceof Literal literal) {
                return Optional.of(literal.getString());
            }
        }
        return Optional.empty();
    }
}
