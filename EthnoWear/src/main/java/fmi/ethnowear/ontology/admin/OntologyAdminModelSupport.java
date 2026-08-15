package fmi.ethnowear.ontology.admin;

import fmi.ethnowear.ontology.admin.model.OntologyReference;
import fmi.ethnowear.ontology.jena.JenaOntologyStore;
import org.apache.jena.ontology.Individual;
import org.apache.jena.ontology.OntClass;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

import static fmi.ethnowear.util.TextUtils.isBlank;

@Component
public class OntologyAdminModelSupport {

    static final Property SKOS_ALT_LABEL = ResourceFactory.createProperty(
            "http://www.w3.org/2004/02/skos/core#",
            "altLabel"
    );

    private static final Set<String> MANAGED_LANGUAGES = Set.of("bg", "en");

    private final JenaOntologyStore store;

    public OntologyAdminModelSupport(JenaOntologyStore store) {
        this.store = store;
    }

    public <T> Optional<T> findIndividual(
            String localName,
            String parentClassLocalName,
            BiFunction<OntModel, Individual, T> mapper
    ) {
        return store.read(model -> individualOfType(model, localName, parentClassLocalName)
                .map(individual -> mapper.apply(model, individual)));
    }

    public <T> List<T> listIndividuals(
            String parentClassLocalName,
            Set<String> allowedDirectTypes,
            BiFunction<OntModel, Individual, T> mapper,
            Function<T, String> localName
    ) {
        return store.read(model -> {
            OntClass parentClass = model.getOntClass(store.uri(parentClassLocalName));

            if (parentClass == null)
                return List.of();

            return model.listIndividuals()
                    .filterKeep(individual -> individual.getURI() != null)
                    .filterKeep(individual -> isInstanceOfClassOrSubclass(model, individual, parentClass))
                    .filterKeep(individual -> !directTypes(individual, allowedDirectTypes).isEmpty())
                    .mapWith(individual -> mapper.apply(model, individual))
                    .toList()
                    .stream()
                    .sorted(Comparator.comparing(localName, String.CASE_INSENSITIVE_ORDER))
                    .toList();
        });
    }

    public Optional<Individual> individualOfType(
            @NonNull OntModel model,
            String localName,
            String parentClassLocalName
    ) {
        Individual individual = model.getIndividual(store.uri(localName));
        OntClass parentClass = model.getOntClass(store.uri(parentClassLocalName));

        if (individual == null
                || parentClass == null
                || !isInstanceOfClassOrSubclass(model, individual, parentClass))
            return Optional.empty();

        return Optional.of(individual);
    }

    public Set<String> directTypes(@NonNull Individual individual, Set<String> allowedTypes) {
        Set<String> types = new LinkedHashSet<>();

        individual.listRDFTypes(true)
                .filterKeep(resource -> resource.getLocalName() != null)
                .forEachRemaining(resource -> {
                    if (allowedTypes.contains(resource.getLocalName()))
                        types.add(resource.getLocalName());
                });

        return Set.copyOf(types);
    }

    public Set<String> objectLocalNames(
            @NonNull OntModel model,
            Resource resource,
            Property property
    ) {
        Set<String> localNames = new LinkedHashSet<>();

        model.listObjectsOfProperty(resource, property)
                .filterKeep(RDFNode::isResource)
                .mapWith(RDFNode::asResource)
                .filterKeep(value -> value.getLocalName() != null)
                .forEachRemaining(value -> localNames.add(value.getLocalName()));

        return Set.copyOf(localNames);
    }

    public List<OntologyReference> incomingReferences(@NonNull OntModel model, Resource resource) {
        List<OntologyReference> references = new ArrayList<>();
        StmtIterator statements = model.listStatements(null, null, resource);

        while (statements.hasNext()) {
            Statement statement = statements.nextStatement();
            Resource subject = statement.getSubject();
            references.add(new OntologyReference(
                    subject.getLocalName() == null ? subject.toString() : subject.getLocalName(),
                    statement.getPredicate().getLocalName()
            ));
        }

        return List.copyOf(references);
    }

    public void removeManagedTypes(
            @NonNull OntModel model,
            Individual individual,
            Set<String> allowedTypes
    ) {
        List<Statement> managedTypes = model.listStatements(individual, RDF.type, (RDFNode) null)
                .filterKeep(statement -> statement.getObject().isResource())
                .filterKeep(statement -> {
                    String localName = statement.getResource().getLocalName();
                    return localName != null && allowedTypes.contains(localName);
                })
                .toList();

        model.remove(managedTypes);
    }

    public void addManagedLocalizedValues(
            OntModel model,
            Resource resource,
            String labelBg,
            String labelEn,
            List<String> altLabelsBg,
            List<String> altLabelsEn,
            String commentBg,
            String commentEn
    ) {
        addLocalizedLiteral(model, resource, RDFS.label, labelBg, "bg");
        addLocalizedLiteral(model, resource, RDFS.label, labelEn, "en");
        addLocalizedLiterals(model, resource, SKOS_ALT_LABEL, altLabelsBg, "bg");
        addLocalizedLiterals(model, resource, SKOS_ALT_LABEL, altLabelsEn, "en");
        addLocalizedLiteral(model, resource, RDFS.comment, commentBg, "bg");
        addLocalizedLiteral(model, resource, RDFS.comment, commentEn, "en");
    }

    public void replaceLocalizedValues(
            OntModel model,
            Resource resource,
            Property property,
            String valueBg,
            String valueEn
    ) {
        removeLocalizedValues(model, resource, property);
        addLocalizedLiteral(model, resource, property, valueBg, "bg");
        addLocalizedLiteral(model, resource, property, valueEn, "en");
    }

    public void replaceLocalizedValues(
            OntModel model,
            Resource resource,
            Property property,
            List<String> valuesBg,
            List<String> valuesEn
    ) {
        removeLocalizedValues(model, resource, property);
        addLocalizedLiterals(model, resource, property, valuesBg, "bg");
        addLocalizedLiterals(model, resource, property, valuesEn, "en");
    }

    public void addLocalizedLiterals(
            OntModel model,
            Resource resource,
            Property property,
            @NonNull List<String> values,
            String language
    ) {
        values.forEach(value -> addLocalizedLiteral(model, resource, property, value, language));
    }

    public void addLocalizedLiteral(
            OntModel model,
            Resource resource,
            Property property,
            String value,
            String language
    ) {
        if (!isBlank(value))
            resource.addProperty(property, model.createLiteral(value.trim(), language));
    }

    public Optional<String> firstLiteral(
            OntModel model,
            Resource resource,
            Property property,
            String language
    ) {
        return literals(model, resource, property, language).stream().findFirst();
    }

    public List<String> literals(
            @NonNull OntModel model,
            Resource resource,
            Property property,
            String language
    ) {
        List<String> values = new ArrayList<>();
        StmtIterator statements = model.listStatements(resource, property, (RDFNode) null);

        while (statements.hasNext()) {
            RDFNode value = statements.nextStatement().getObject();

            if (value.isLiteral() && language.equalsIgnoreCase(value.asLiteral().getLanguage()))
                values.add(value.asLiteral().getString());
        }

        return List.copyOf(values);
    }

    private void removeLocalizedValues(
            @NonNull OntModel model,
            Resource resource,
            Property property
    ) {
        List<Statement> statements = model.listStatements(resource, property, (RDFNode) null)
                .filterKeep(statement -> statement.getObject().isLiteral())
                .filterKeep(statement -> MANAGED_LANGUAGES.contains(
                        statement.getLiteral().getLanguage().toLowerCase()
                ))
                .toList();

        model.remove(statements);
    }

    private boolean isInstanceOfClassOrSubclass(
            OntModel model,
            @NonNull Individual individual,
            OntClass expectedClass
    ) {
        Deque<Resource> pending = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();

        individual.listRDFTypes(false)
                .filterKeep(Resource::isURIResource)
                .forEachRemaining(pending::addLast);

        while (!pending.isEmpty()) {
            Resource currentClass = pending.removeFirst();
            String currentUri = currentClass.getURI();

            if (currentUri == null || !visited.add(currentUri))
                continue;

            if (currentUri.equals(expectedClass.getURI()))
                return true;

            currentClass.listProperties(org.apache.jena.vocabulary.RDFS.subClassOf)
                    .filterKeep(statement -> statement.getObject().isURIResource())
                    .mapWith(statement -> statement.getResource())
                    .forEachRemaining(pending::addLast);
        }

        return false;
    }
}
