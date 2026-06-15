package fmi.ethnowear.ontology.jena;

import fmi.ethnowear.ontology.model.LocalizedOntologyResource;
import fmi.ethnowear.ontology.model.OntologyLanguage;
import fmi.ethnowear.ontology.model.OntologyResource;
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
        return store.read(model -> {
            OntClass ontClass = model.getOntClass(toUri(classLocalName));
            Property targetProperty = model.getProperty(toUri(propertyLocalName));
            if (ontClass == null || targetProperty == null) {
                return Optional.empty();
            }

            StmtIterator superClasses = model.listStatements(ontClass, RDFS.subClassOf, (RDFNode) null);
            while (superClasses.hasNext()) {
                RDFNode node = superClasses.nextStatement().getObject();
                if (!node.isResource()) {
                    continue;
                }

                Resource restriction = node.asResource();
                if (!model.contains(restriction, OWL.onProperty, targetProperty)) {
                    continue;
                }

                StmtIterator values = model.listStatements(restriction, OWL.hasValue, (RDFNode) null);
                while (values.hasNext()) {
                    RDFNode value = values.nextStatement().getObject();
                    if (value.isResource() && value.asResource().getURI() != null) {
                        return Optional.of(toResource(model, value.asResource()));
                    }
                }
            }
            return Optional.empty();
        });
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

    private LocalizedOntologyResource toLocalizedResource(
            OntModel model,
            Resource resource,
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

    private Optional<String> preferredLabel(OntModel model, Resource resource, OntologyLanguage language) {
        return literalValues(model, resource, RDFS.label, language).stream().findFirst();
    }

    private Optional<String> comment(OntModel model, Resource resource, OntologyLanguage language) {
        return literalValues(model, resource, RDFS.comment, language).stream().findFirst();
    }

    private List<String> literalValues(
            OntModel model,
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
            Resource resource,
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

    private OntologyResource toResource(OntModel model, Resource resource) {
        return new OntologyResource(
                resource.getURI(),
                resource.getLocalName(),
                labelFor(model, resource).orElse(null)
        );
    }

    private Optional<String> labelFor(OntModel model, Resource resource) {
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
