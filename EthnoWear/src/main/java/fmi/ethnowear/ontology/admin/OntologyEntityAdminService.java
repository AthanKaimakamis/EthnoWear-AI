package fmi.ethnowear.ontology.admin;

import fmi.ethnowear.application.enums.OntologyEntityKind;
import fmi.ethnowear.application.exceptions.OntologyEntityException;
import fmi.ethnowear.ontology.OntologyTerms;
import fmi.ethnowear.ontology.admin.command.OntologyEntityCommand;
import fmi.ethnowear.ontology.admin.model.OntologyEntityDetails;
import fmi.ethnowear.ontology.admin.model.OntologyReference;
import fmi.ethnowear.ontology.jena.JenaOntologyStore;
import org.apache.jena.ontology.Individual;
import org.apache.jena.ontology.OntClass;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.rdf.model.*;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Unmodifiable;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;

import static fmi.ethnowear.util.TextUtils.isBlank;
import static fmi.ethnowear.util.TextUtils.isNotBlank;

@Service
public class OntologyEntityAdminService {
    private static final Property SKOS_ALT_LABEL = ResourceFactory.createProperty(
            "http://www.w3.org/2004/02/skos/core#", "altLabel");
    private static final Pattern LOCAL_NAME = Pattern.compile("[A-Za-z][A-Za-z0-9_]*");

    private final JenaOntologyStore store;

    public OntologyEntityAdminService(JenaOntologyStore store) {
        this.store = store;
    }

    public List<OntologyEntityDetails> list(OntologyEntityKind kind) {
        return store.read(model -> resources(model, kind).stream()
                .map(resource -> details(model, resource, kind))
                .sorted(Comparator.comparing(OntologyEntityDetails::localName, String.CASE_INSENSITIVE_ORDER))
                .toList());
    }

    public OntologyEntityDetails get(OntologyEntityKind kind, String localName) {
        validateLocalName(localName);
        return store.read(model -> details(model, required(model, kind, localName), kind));
    }

    public OntologyEntityDetails create(OntologyEntityKind kind, OntologyEntityCommand command) {
        validate(command, true);
        store.write(model -> {
            Resource candidate = model.getResource(store.uri(command.localName()));

            if (model.containsResource(candidate))
                throw error(OntologyEntityException.Reason.ALREADY_EXISTS,
                        "Ontology resource already exists: " + command.localName());

            Resource resource;
            if (kind == OntologyEntityKind.REGIONAL_EMBROIDERY) {
                OntClass parent = requiredClass(model, OntologyTerms.Classes.REGIONAL_EMBROIDERY);
                OntClass ontologyClass = model.createClass(candidate.getURI());
                ontologyClass.addSuperClass(parent);
                resource = ontologyClass;
            } else {
                resource = model.createIndividual(candidate.getURI(), requiredClass(model, className(kind)));
            }
            apply(model, resource, kind, command);

            return null;
        });

        return get(kind, command.localName());
    }

    public OntologyEntityDetails update(
            OntologyEntityKind kind,
            String localName,
            OntologyEntityCommand command
    ) {
        validateLocalName(localName);
        validate(command, false);
        store.write(model -> {
            Resource resource = required(model, kind, localName);
            apply(model, resource, kind, command);
            return null;
        });

        return get(kind, localName);
    }

    public void delete(OntologyEntityKind kind, String localName) {
        validateLocalName(localName);
        store.write(model -> {
            Resource resource = required(model, kind, localName);
            List<OntologyReference> references = incomingReferences(model, resource);

            if (!references.isEmpty())
                throw new OntologyEntityException(
                        OntologyEntityException.Reason.IN_USE,
                        "Ontology resource is referenced and cannot be deleted: " + localName,
                        references
                );

            if (kind == OntologyEntityKind.REGIONAL_EMBROIDERY)
                removeManagedRestrictions(model, resource);

            model.removeAll(resource, null, (RDFNode) null);
            return null;
        });
    }

    private void apply(OntModel model, Resource resource, OntologyEntityKind kind, @NonNull OntologyEntityCommand command) {
        replaceLocalized(model, resource, RDFS.label, command.labelBg(), command.labelEn());
        replaceLocalized(model, resource, RDFS.comment, command.commentBg(), command.commentEn());
        replaceLocalized(model, resource, command.altLabelsBg(), command.altLabelsEn());

        if (kind == OntologyEntityKind.REGION) {
            replaceDirect(model, resource, OntologyTerms.ObjectProperties.BELONGS_TO_REGION_GROUP,
                    nullableSet(command.regionGroupLocalName()), OntologyTerms.Classes.REGION_GROUP);
            replaceDirect(model, resource, OntologyTerms.ObjectProperties.REGION_USES_ORNAMENT,
                    command.ornamentLocalNames(), OntologyTerms.Classes.ORNAMENT);
            replaceDirect(model, resource, OntologyTerms.ObjectProperties.REGION_USES_TECHNIQUE,
                    command.techniqueLocalNames(), OntologyTerms.Classes.TECHNIQUE);
        } else if (kind == OntologyEntityKind.MOTIF) {
            ensureMotifRegionProperty(model);
            replaceDirect(model, resource, OntologyTerms.ObjectProperties.MOTIF_HAS_REGION,
                    nullableSet(command.regionLocalName()), OntologyTerms.Classes.REGION);
            replaceDirect(model, resource, OntologyTerms.ObjectProperties.MOTIF_HAS_ORNAMENT,
                    command.ornamentLocalNames(), OntologyTerms.Classes.ORNAMENT);
            replaceDirect(model, resource, OntologyTerms.ObjectProperties.MOTIF_HAS_TECHNIQUE,
                    command.techniqueLocalNames(), OntologyTerms.Classes.TECHNIQUE);
        } else {
            removeManagedRestrictions(model, resource);
            addRestrictions(model, resource, OntologyTerms.ObjectProperties.HAS_REGION,
                    nullableSet(command.regionLocalName()), OntologyTerms.Classes.REGION);
            addRestrictions(model, resource, OntologyTerms.ObjectProperties.HAS_ORNAMENT,
                    command.ornamentLocalNames(), OntologyTerms.Classes.ORNAMENT);
            addRestrictions(model, resource, OntologyTerms.ObjectProperties.HAS_TECHNIQUE,
                    command.techniqueLocalNames(), OntologyTerms.Classes.TECHNIQUE);
            addRestrictions(model, resource, OntologyTerms.ObjectProperties.HAS_MOTIF,
                    command.motifLocalNames(), OntologyTerms.Classes.MOTIF);
        }
    }

    private List<Resource> resources(OntModel model, OntologyEntityKind kind) {
        if (kind == OntologyEntityKind.REGIONAL_EMBROIDERY) {
            OntClass parent = requiredClass(model, OntologyTerms.Classes.REGIONAL_EMBROIDERY);
            return parent.listSubClasses(true).filterKeep(resource -> resource.getURI() != null)
                    .mapWith(resource -> (Resource) resource).toList();
        }
        OntClass type = requiredClass(model, className(kind));

        return model.listIndividuals(type).filterKeep(resource -> resource.getURI() != null)
                .mapWith(resource -> (Resource) resource).toList();
    }

    private @NonNull Resource required(@NonNull OntModel model, OntologyEntityKind kind, String localName) {
        Resource resource = model.getResource(store.uri(localName));
        boolean exists;

        if (kind == OntologyEntityKind.REGIONAL_EMBROIDERY) {
            OntClass ontologyClass = model.getOntClass(resource.getURI());
            OntClass parent = requiredClass(model, OntologyTerms.Classes.REGIONAL_EMBROIDERY);
            exists = ontologyClass != null && ontologyClass.hasSuperClass(parent, false) && !ontologyClass.equals(parent);
        } else {
            Individual individual = model.getIndividual(resource.getURI());
            exists = individual != null && individual.hasOntClass(requiredClass(model, className(kind)), false);
        }

        if (!exists)
            throw error(OntologyEntityException.Reason.NOT_FOUND, "Ontology resource not found: " + localName);

        return resource;
    }

    @Contract("_, _, _ -> new")
    private @NonNull OntologyEntityDetails details(OntModel model, Resource resource, OntologyEntityKind kind) {
        Set<String> ornaments = new LinkedHashSet<>();
        Set<String> techniques = new LinkedHashSet<>();
        Set<String> motifs = new LinkedHashSet<>();
        String region = null;
        String regionGroup = null;

        if (kind == OntologyEntityKind.REGION) {
            regionGroup = firstObject(model, resource, OntologyTerms.ObjectProperties.BELONGS_TO_REGION_GROUP);
            ornaments.addAll(objects(model, resource, OntologyTerms.ObjectProperties.REGION_USES_ORNAMENT));
            techniques.addAll(objects(model, resource, OntologyTerms.ObjectProperties.REGION_USES_TECHNIQUE));
        } else if (kind == OntologyEntityKind.MOTIF) {
            region = firstObject(model, resource, OntologyTerms.ObjectProperties.MOTIF_HAS_REGION);
            ornaments.addAll(objects(model, resource, OntologyTerms.ObjectProperties.MOTIF_HAS_ORNAMENT));
            techniques.addAll(objects(model, resource, OntologyTerms.ObjectProperties.MOTIF_HAS_TECHNIQUE));
        } else {
            Map<String, Set<String>> restrictions = restrictions(model, resource);
            region = restrictions.getOrDefault(OntologyTerms.ObjectProperties.HAS_REGION, Set.of())
                    .stream().findFirst().orElse(null);
            ornaments.addAll(restrictions.getOrDefault(OntologyTerms.ObjectProperties.HAS_ORNAMENT, Set.of()));
            techniques.addAll(restrictions.getOrDefault(OntologyTerms.ObjectProperties.HAS_TECHNIQUE, Set.of()));
            motifs.addAll(restrictions.getOrDefault(OntologyTerms.ObjectProperties.HAS_MOTIF, Set.of()));
        }

        return new OntologyEntityDetails(resource.getURI(), resource.getLocalName(),
                firstLiteral(model, resource, RDFS.label, "bg"), firstLiteral(model, resource, RDFS.label, "en"),
                literals(model, resource, SKOS_ALT_LABEL, "bg"), literals(model, resource, SKOS_ALT_LABEL, "en"),
                firstLiteral(model, resource, RDFS.comment, "bg"), firstLiteral(model, resource, RDFS.comment, "en"),
                regionGroup, region, Set.copyOf(ornaments), Set.copyOf(techniques), Set.copyOf(motifs));
    }

    private void replaceDirect(@NonNull OntModel model, Resource subject, String propertyName,
                               @NonNull Set<String> targets, String expectedClass) {
        Property property = model.getProperty(store.uri(propertyName));
        model.removeAll(subject, property, null);

        targets.forEach(target ->
                subject.addProperty(property, requiredIndividual(model, target, expectedClass))
        );
    }

    private void addRestrictions(@NonNull OntModel model, Resource subject, String propertyName,
                                 @NonNull Set<String> targets, String expectedClass) {
        Property property = model.getProperty(store.uri(propertyName));

        targets.forEach(target -> {
            Individual value = requiredIndividual(model, target, expectedClass);
            Resource restriction = model.createResource();
            restriction.addProperty(RDF.type, OWL.Restriction);
            restriction.addProperty(OWL.onProperty, property);
            restriction.addProperty(OWL.hasValue, value);
            subject.addProperty(RDFS.subClassOf, restriction);
        });
    }

    private @NonNull Map<String, Set<String>> restrictions(@NonNull OntModel model, Resource subject) {
        Map<String, Set<String>> result = new HashMap<>();
        model.listObjectsOfProperty(subject, RDFS.subClassOf).filterKeep(RDFNode::isResource)
                .mapWith(RDFNode::asResource).forEachRemaining(restriction -> {
                    Statement property = restriction.getProperty(OWL.onProperty);
                    Statement value = restriction.getProperty(OWL.hasValue);

                    if (property != null && value != null && property.getObject().isResource()
                            && value.getObject().isResource()) {
                        String propertyName = property.getResource().getLocalName();
                        String valueName = value.getResource().getLocalName();

                        if (propertyName != null && valueName != null)
                            result.computeIfAbsent(propertyName, ignored -> new LinkedHashSet<>()).add(valueName);
                    }
                });

        return result;
    }

    private void removeManagedRestrictions(@NonNull OntModel model, Resource subject) {
        Set<String> managed = Set.of(OntologyTerms.ObjectProperties.HAS_REGION,
                OntologyTerms.ObjectProperties.HAS_ORNAMENT, OntologyTerms.ObjectProperties.HAS_TECHNIQUE,
                OntologyTerms.ObjectProperties.HAS_MOTIF);

        List<Resource> restrictions = model.listObjectsOfProperty(subject, RDFS.subClassOf)
                .filterKeep(RDFNode::isAnon).mapWith(RDFNode::asResource)
                .filterKeep(resource -> {
                    Statement property = resource.getProperty(OWL.onProperty);
                    return property != null && property.getObject().isResource()
                            && managed.contains(property.getResource().getLocalName());
                }).toList();

        restrictions.forEach(resource -> {
            model.removeAll(subject, RDFS.subClassOf, resource);
            model.removeAll(resource, null, (RDFNode) null);
        });
    }

    private void ensureMotifRegionProperty(@NonNull OntModel model) {
        String uri = store.uri(OntologyTerms.ObjectProperties.MOTIF_HAS_REGION);

        if (model.getObjectProperty(uri) == null) {
            var property = model.createObjectProperty(uri);
            property.addDomain(requiredClass(model, OntologyTerms.Classes.MOTIF));
            property.addRange(requiredClass(model, OntologyTerms.Classes.REGION));
        }
    }

    private @NonNull Individual requiredIndividual(@NonNull OntModel model, String localName, String expectedClassLocalName) {
        validateLocalName(localName);
        Individual individual = model.getIndividual(store.uri(localName));

        if (individual == null)
            throw error(OntologyEntityException.Reason.INVALID, "Ontology individual does not exist: " + localName);

        OntClass expectedClass = requiredClass(model, expectedClassLocalName);

        if (!isInstanceOfClassOrSubclass(model, individual, expectedClass))
            throw error(OntologyEntityException.Reason.INVALID, localName + " is not an instance of " + expectedClassLocalName + " or one of its subclasses");

        return individual;
    }

    private boolean isInstanceOfClassOrSubclass(OntModel model, @NonNull Individual individual, OntClass expectedClass) {
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

            model.listObjectsOfProperty(currentClass, RDFS.subClassOf)
                    .filterKeep(RDFNode::isURIResource)
                    .mapWith(RDFNode::asResource)
                    .forEachRemaining(pending::addLast);
        }

        return false;
    }

    private @NonNull OntClass requiredClass(@NonNull OntModel model, String localName) {
        OntClass ontologyClass = model.getOntClass(store.uri(localName));

        if (ontologyClass == null)
            throw error(OntologyEntityException.Reason.INVALID, "Ontology class does not exist: " + localName);

        return ontologyClass;
    }

    private List<OntologyReference> incomingReferences(@NonNull OntModel model, Resource resource) {
        return model.listStatements(null, null, resource)
                .filterKeep(statement -> !statement.getSubject().isAnon())
                .mapWith(statement -> new OntologyReference(
                        statement.getSubject().getLocalName(),
                        statement.getPredicate().getLocalName())
                )
                .toList();
    }

    private @NonNull Set<String> objects(@NonNull OntModel model, Resource subject, String propertyName) {
        Set<String> result = new LinkedHashSet<>();
        model.listObjectsOfProperty(subject, model.getProperty(store.uri(propertyName)))
                .filterKeep(RDFNode::isResource).mapWith(RDFNode::asResource)
                .filterKeep(resource -> resource.getLocalName() != null)
                .forEachRemaining(resource -> result.add(resource.getLocalName()));

        return result;
    }

    private String firstObject(OntModel model, Resource subject, String propertyName) {
        return objects(model, subject, propertyName).stream().findFirst().orElse(null);
    }

    private void replaceLocalized(@NonNull OntModel model, Resource resource, Property property, String bg, String en) {
        model.removeAll(resource, property, null);
        addLiteral(model, resource, property, bg, "bg");
        addLiteral(model, resource, property, en, "en");
    }

    private void replaceLocalized(@NonNull OntModel model, Resource resource,
                                  @NonNull List<String> bg, @NonNull List<String> en) {
        model.removeAll(resource, OntologyEntityAdminService.SKOS_ALT_LABEL, null);
        bg.forEach(value -> addLiteral(model, resource, OntologyEntityAdminService.SKOS_ALT_LABEL, value, "bg"));
        en.forEach(value -> addLiteral(model, resource, OntologyEntityAdminService.SKOS_ALT_LABEL, value, "en"));
    }

    private void addLiteral(OntModel model, Resource resource, Property property, String value, String language) {
        if (isNotBlank(value))
            resource.addProperty(property, model.createLiteral(value.trim(), language));
    }

    private String firstLiteral(OntModel model, Resource resource, Property property, String language) {
        return literals(model, resource, property, language).stream().findFirst().orElse(null);
    }

    private @NonNull @Unmodifiable List<String> literals(@NonNull OntModel model, Resource resource, Property property, String language) {
        List<String> values = new ArrayList<>();
        model.listObjectsOfProperty(resource, property).filterKeep(RDFNode::isLiteral)
                .mapWith(RDFNode::asLiteral).filterKeep(literal -> language.equals(literal.getLanguage()))
                .forEachRemaining(literal -> values.add(literal.getString()));

        return List.copyOf(values);
    }

    private void validate(OntologyEntityCommand command, boolean creating) {
        if (command == null)
            throw error(OntologyEntityException.Reason.INVALID, "Request body is required");

        if (creating) validateLocalName(command.localName());

        if (isBlank(command.labelBg()) && isBlank(command.labelEn()))
            throw error(OntologyEntityException.Reason.INVALID, "At least one localized label is required");
    }

    private void validateLocalName(String localName) {
        if (localName == null || !LOCAL_NAME.matcher(localName).matches())
            throw error(OntologyEntityException.Reason.INVALID, "Invalid ontology local name: " + localName);
    }

    private @NonNull @Unmodifiable Set<String> nullableSet(String value) {
        return isBlank(value) ? Set.of() : Set.of(value);
    }

    @Contract(pure = true)
    private String className(@NonNull OntologyEntityKind kind) {
        return switch (kind) {
            case REGION -> OntologyTerms.Classes.REGION;
            case MOTIF -> OntologyTerms.Classes.MOTIF;
            case REGIONAL_EMBROIDERY -> OntologyTerms.Classes.REGIONAL_EMBROIDERY;
        };
    }

    @Contract("_, _ -> new")
    private @NonNull OntologyEntityException error(OntologyEntityException.Reason reason, String message) {
        return new OntologyEntityException(reason, message);
    }
}
