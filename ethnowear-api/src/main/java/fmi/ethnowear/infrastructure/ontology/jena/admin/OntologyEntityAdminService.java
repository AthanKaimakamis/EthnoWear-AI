package fmi.ethnowear.infrastructure.ontology.jena.admin;

import fmi.ethnowear.domain.model.ontology.OntologyEntityKind;
import fmi.ethnowear.application.exception.OntologyEntityException;
import fmi.ethnowear.domain.constant.ontology.OntologyTerms;
import fmi.ethnowear.application.dto.ontology.admin.OntologyEntityCommand;
import fmi.ethnowear.application.dto.ontology.admin.OntologyEntityDetails;
import fmi.ethnowear.application.dto.ontology.admin.RegionDerivedTypeSynchronizationDetails;
import fmi.ethnowear.application.port.ontology.admin.OntologyEntityAdminPort;
import fmi.ethnowear.application.port.ontology.admin.OntologyUsageGuard;
import fmi.ethnowear.domain.model.ontology.OntologyReference;
import fmi.ethnowear.infrastructure.ontology.jena.JenaOntologyStore;
import org.apache.jena.ontology.Individual;
import org.apache.jena.ontology.OntClass;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.rdf.model.*;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Unmodifiable;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;

import static fmi.ethnowear.util.TextUtils.isBlank;
import static fmi.ethnowear.util.TextUtils.isNotBlank;

@Service
public class OntologyEntityAdminService implements OntologyEntityAdminPort {
    private static final Property SKOS_ALT_LABEL = ResourceFactory.createProperty(
            "http://www.w3.org/2004/02/skos/core#", "altLabel");
    private static final Pattern LOCAL_NAME = Pattern.compile("[A-Za-z][A-Za-z0-9_]*");
    private static final List<DerivedRegionTypeDefinition> DERIVED_REGION_TYPES = List.of(
            new DerivedRegionTypeDefinition(
                    OntologyEntityKind.REGIONAL_EMBROIDERY,
                    OntologyTerms.ObjectProperties.HAS_REGION,
                    "Embroidery",
                    "Шевица - ",
                    "Embroidery - "
            ),
            new DerivedRegionTypeDefinition(
                    OntologyEntityKind.REGIONAL_MOTIF,
                    OntologyTerms.ObjectProperties.MOTIF_HAS_REGION,
                    "Motif",
                    "Мотив - ",
                    "Motif - "
            )
    );

    private final JenaOntologyStore store;
    private final OntologyUsageGuard usageGuard;

    public OntologyEntityAdminService(JenaOntologyStore store, OntologyUsageGuard usageGuard) {
        this.store = store;
        this.usageGuard = usageGuard;
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
        validate(kind, command, true);
        store.write(model -> {
            Resource candidate = model.getResource(store.uri(command.localName()));

            if (model.containsResource(candidate))
                throw error(OntologyEntityException.Reason.ALREADY_EXISTS,
                        "Ontology resource already exists: " + command.localName());

            Resource resource;
            if (isRegionalType(kind)) {
                OntClass parent = ensureRegionalParent(model, kind);
                OntClass ontologyClass = model.createClass(candidate.getURI());
                ontologyClass.addSuperClass(parent);
                resource = ontologyClass;
            } else {
                resource = model.createIndividual(candidate.getURI(), requiredClass(model, className(kind)));
            }
            apply(model, resource, kind, command);
            if (kind == OntologyEntityKind.REGION)
                synchronizeDerivedRegionTypes(model, List.of(resource));

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
        validate(kind, command, false);
        if (isNotBlank(command.localName()) && !localName.equals(command.localName()))
            throw error(
                    OntologyEntityException.Reason.INVALID,
                    "Ontology local names are immutable; use a dedicated rename operation"
            );
        store.write(model -> {
            Resource resource = required(model, kind, localName);
            apply(model, resource, kind, command);
            if (kind == OntologyEntityKind.REGION)
                synchronizeDerivedRegionTypes(model, List.of(resource));
            return null;
        });

        return get(kind, localName);
    }

    public void delete(OntologyEntityKind kind, String localName) {
        validateLocalName(localName);
        store.write(model -> {
            Resource resource = required(model, kind, localName);
            List<Resource> derivedTypes = kind == OntologyEntityKind.REGION
                    ? ownedDerivedRegionTypes(model, resource)
                    : List.of();
            List<Resource> resourcesToDelete = new ArrayList<>(derivedTypes);
            resourcesToDelete.add(resource);
            usageGuard.requireUnused(resourcesToDelete.stream().map(Resource::getURI).toList());

            for (Resource derivedType : derivedTypes) {
                List<OntologyReference> derivedReferences = incomingReferences(model, derivedType);
                if (!derivedReferences.isEmpty())
                    throw new OntologyEntityException(
                            OntologyEntityException.Reason.IN_USE,
                            "Derived ontology resource is referenced and cannot be deleted: "
                                    + derivedType.getLocalName(),
                            derivedReferences
                    );
                removeResource(model, derivedType, true);
            }

            List<OntologyReference> references = incomingReferences(model, resource);

            if (!references.isEmpty())
                throw new OntologyEntityException(
                        OntologyEntityException.Reason.IN_USE,
                        "Ontology resource is referenced and cannot be deleted: " + localName,
                        references
                );

            removeResource(model, resource, isRegionalType(kind));
            return null;
        });
    }

    @Override
    public RegionDerivedTypeSynchronizationDetails synchronizeRegionDerivedTypes() {
        return store.write(model -> {
            OntClass regionClass = requiredClass(model, OntologyTerms.Classes.REGION);
            List<Resource> regions = regionClass.listInstances()
                    .filterKeep(Resource::isURIResource)
                    .mapWith(resource -> (Resource) resource)
                    .toList();
            return synchronizeDerivedRegionTypes(model, regions);
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
        } else if (kind == OntologyEntityKind.REGIONAL_EMBROIDERY) {
            removeManagedRestrictions(model, resource);
            addRestrictions(model, resource, OntologyTerms.ObjectProperties.HAS_REGION,
                    nullableSet(command.regionLocalName()), OntologyTerms.Classes.REGION);
            addRestrictions(model, resource, OntologyTerms.ObjectProperties.HAS_ORNAMENT,
                    command.ornamentLocalNames(), OntologyTerms.Classes.ORNAMENT);
            addRestrictions(model, resource, OntologyTerms.ObjectProperties.HAS_TECHNIQUE,
                    command.techniqueLocalNames(), OntologyTerms.Classes.TECHNIQUE);
            addRestrictions(model, resource, OntologyTerms.ObjectProperties.HAS_MOTIF,
                    command.motifLocalNames(), OntologyTerms.Classes.MOTIF);
        } else {
            removeManagedRestrictions(model, resource);
            addRestrictions(model, resource, OntologyTerms.ObjectProperties.MOTIF_HAS_REGION,
                    nullableSet(command.regionLocalName()), OntologyTerms.Classes.REGION);
            addRestrictions(model, resource, OntologyTerms.ObjectProperties.MOTIF_HAS_ORNAMENT,
                    command.ornamentLocalNames(), OntologyTerms.Classes.ORNAMENT);
            addRestrictions(model, resource, OntologyTerms.ObjectProperties.MOTIF_HAS_TECHNIQUE,
                    command.techniqueLocalNames(), OntologyTerms.Classes.TECHNIQUE);
        }
    }

    private List<Resource> resources(OntModel model, OntologyEntityKind kind) {
        if (isRegionalType(kind)) {
            OntClass parent = regionalParent(model, kind);
            if (parent == null)
                return List.of();

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

        if (isRegionalType(kind)) {
            OntClass ontologyClass = model.getOntClass(resource.getURI());
            OntClass parent = regionalParent(model, kind);
            exists = ontologyClass != null && parent != null
                    && ontologyClass.hasSuperClass(parent, false) && !ontologyClass.equals(parent);
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
        } else if (kind == OntologyEntityKind.REGIONAL_EMBROIDERY) {
            Map<String, Set<String>> restrictions = restrictions(model, resource);
            region = restrictions.getOrDefault(OntologyTerms.ObjectProperties.HAS_REGION, Set.of())
                    .stream().findFirst().orElse(null);
            ornaments.addAll(restrictions.getOrDefault(OntologyTerms.ObjectProperties.HAS_ORNAMENT, Set.of()));
            techniques.addAll(restrictions.getOrDefault(OntologyTerms.ObjectProperties.HAS_TECHNIQUE, Set.of()));
            motifs.addAll(restrictions.getOrDefault(OntologyTerms.ObjectProperties.HAS_MOTIF, Set.of()));
        } else {
            Map<String, Set<String>> restrictions = restrictions(model, resource);
            region = restrictions.getOrDefault(OntologyTerms.ObjectProperties.MOTIF_HAS_REGION, Set.of())
                    .stream().findFirst().orElse(null);
            ornaments.addAll(restrictions.getOrDefault(
                    OntologyTerms.ObjectProperties.MOTIF_HAS_ORNAMENT, Set.of()));
            techniques.addAll(restrictions.getOrDefault(
                    OntologyTerms.ObjectProperties.MOTIF_HAS_TECHNIQUE, Set.of()));
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
        Set<String> managed = Set.of(
                OntologyTerms.ObjectProperties.HAS_REGION,
                OntologyTerms.ObjectProperties.HAS_ORNAMENT,
                OntologyTerms.ObjectProperties.HAS_TECHNIQUE,
                OntologyTerms.ObjectProperties.HAS_MOTIF,
                OntologyTerms.ObjectProperties.MOTIF_HAS_REGION,
                OntologyTerms.ObjectProperties.MOTIF_HAS_ORNAMENT,
                OntologyTerms.ObjectProperties.MOTIF_HAS_TECHNIQUE
        );

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
        Set<OntologyReference> references = new LinkedHashSet<>();
        model.listStatements(null, null, resource).forEachRemaining(statement -> {
            Resource subject = statement.getSubject();
            if (!subject.isAnon()) {
                references.add(new OntologyReference(
                        subject.getLocalName(),
                        statement.getPredicate().getLocalName()
                ));
                return;
            }

            Statement onProperty = subject.getProperty(OWL.onProperty);
            String propertyName = onProperty != null && onProperty.getObject().isURIResource()
                    ? onProperty.getResource().getLocalName()
                    : statement.getPredicate().getLocalName();
            model.listSubjectsWithProperty(RDFS.subClassOf, subject)
                    .filterKeep(Resource::isURIResource)
                    .forEachRemaining(owner -> references.add(new OntologyReference(
                            owner.getLocalName(),
                            propertyName
                    )));
        });
        return List.copyOf(references);
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

    private void validate(
            OntologyEntityKind kind,
            OntologyEntityCommand command,
            boolean creating
    ) {
        if (command == null)
            throw error(OntologyEntityException.Reason.INVALID, "Request body is required");

        if (creating) validateLocalName(command.localName());

        if (isBlank(command.labelBg()) && isBlank(command.labelEn()))
            throw error(OntologyEntityException.Reason.INVALID, "At least one localized label is required");

        if (isRegionalType(kind) && isBlank(command.regionLocalName()))
            throw error(
                    OntologyEntityException.Reason.INVALID,
                    "Region is required for a regional ontology type"
            );
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
            case REGIONAL_MOTIF -> OntologyTerms.Classes.REGIONAL_MOTIF;
        };
    }

    private boolean isRegionalType(OntologyEntityKind kind) {
        return kind == OntologyEntityKind.REGIONAL_EMBROIDERY
                || kind == OntologyEntityKind.REGIONAL_MOTIF;
    }

    private OntClass requiredRegionalParent(OntModel model, OntologyEntityKind kind) {
        return requiredClass(model, className(kind));
    }

    private OntClass ensureRegionalParent(OntModel model, OntologyEntityKind kind) {
        if (kind == OntologyEntityKind.REGIONAL_MOTIF)
            return ensureRegionalMotifClass(model);

        return requiredClass(model, OntologyTerms.Classes.REGIONAL_EMBROIDERY);
    }

    private OntClass regionalParent(OntModel model, OntologyEntityKind kind) {
        return model.getOntClass(store.uri(className(kind)));
    }

    private OntClass ensureRegionalMotifClass(OntModel model) {
        String uri = store.uri(OntologyTerms.Classes.REGIONAL_MOTIF);
        OntClass regionalMotif = model.getOntClass(uri);
        if (regionalMotif != null) {
            OntClass motif = requiredClass(model, OntologyTerms.Classes.MOTIF);
            if (!regionalMotif.hasSuperClass(motif, true))
                regionalMotif.addSuperClass(motif);
            return regionalMotif;
        }

        regionalMotif = createAvailableClass(model, uri);
        regionalMotif.addSuperClass(requiredClass(model, OntologyTerms.Classes.MOTIF));
        addLiteral(model, regionalMotif, RDFS.label, "Регионален мотив", "bg");
        addLiteral(model, regionalMotif, RDFS.label, "Regional motif", "en");
        return regionalMotif;
    }

    private RegionDerivedTypeSynchronizationDetails synchronizeDerivedRegionTypes(
            OntModel model,
            List<Resource> regions
    ) {
        for (Resource region : regions)
            for (DerivedRegionTypeDefinition definition : DERIVED_REGION_TYPES)
                requireAvailableOrOwned(model, region, definition);

        ensureRegionalMotifClass(model);

        RegionDerivedTypeSynchronizationDetails result =
                RegionDerivedTypeSynchronizationDetails.empty();
        for (Resource region : regions) {
            for (DerivedRegionTypeDefinition definition : DERIVED_REGION_TYPES) {
                result = result.add(synchronizeDerivedRegionType(model, region, definition));
            }
        }
        return result;
    }

    private RegionDerivedTypeSynchronizationDetails synchronizeDerivedRegionType(
            OntModel model,
            Resource region,
            DerivedRegionTypeDefinition definition
    ) {
        String uri = derivedRegionTypeUri(region, definition);
        OntClass derived = model.getOntClass(uri);
        String expectedBg = prefixed(
                definition.bgPrefix(),
                firstLiteral(model, region, RDFS.label, "bg")
        );
        String expectedEn = prefixed(
                definition.enPrefix(),
                firstLiteral(model, region, RDFS.label, "en")
        );

        if (derived == null) {
            derived = model.createClass(uri);
            markGenerated(model, derived, region);
            derived.addSuperClass(requiredRegionalParent(model, definition.kind()));
            replaceGeneratedLabels(model, derived, expectedBg, expectedEn);
            replaceRestrictionsForProperty(
                    model,
                    derived,
                    definition.regionProperty(),
                    Set.of(region.getLocalName()),
                    OntologyTerms.Classes.REGION
            );
            return new RegionDerivedTypeSynchronizationDetails(1, 0, 0);
        }

        OntClass expectedParent = requiredRegionalParent(model, definition.kind());
        boolean updateRequired = !derived.hasSuperClass(expectedParent, true)
                || hasIncorrectManagedParent(model, derived, expectedParent)
                || !hasExactLocalizedValue(model, derived, RDFS.label, expectedBg, "bg")
                || !hasExactLocalizedValue(model, derived, RDFS.label, expectedEn, "en")
                || !restrictions(model, derived)
                .getOrDefault(definition.regionProperty(), Set.of())
                .equals(Set.of(region.getLocalName()));

        if (!updateRequired)
            return new RegionDerivedTypeSynchronizationDetails(0, 0, 1);

        removeIncorrectManagedParents(model, derived, expectedParent);
        if (!derived.hasSuperClass(expectedParent, true))
            derived.addSuperClass(expectedParent);
        replaceGeneratedLabels(model, derived, expectedBg, expectedEn);
        replaceRestrictionsForProperty(
                model,
                derived,
                definition.regionProperty(),
                Set.of(region.getLocalName()),
                OntologyTerms.Classes.REGION
        );
        return new RegionDerivedTypeSynchronizationDetails(0, 1, 0);
    }

    private void requireAvailableOrOwned(
            OntModel model,
            Resource region,
            DerivedRegionTypeDefinition definition
    ) {
        String uri = derivedRegionTypeUri(region, definition);
        Resource candidate = model.getResource(uri);
        if (!model.containsResource(candidate))
            return;

        OntClass ontologyClass = model.getOntClass(uri);
        if (ontologyClass == null || !isOwnedByRegion(model, ontologyClass, region))
            throw error(
                    OntologyEntityException.Reason.ALREADY_EXISTS,
                    "Generated ontology local name is already owned by an unmanaged resource: "
                            + candidate.getLocalName()
            );
    }

    private boolean isOwnedByRegion(OntModel model, Resource resource, Resource region) {
        Property generatedProperty = annotationProperty(
                model,
                OntologyTerms.AnnotationProperties.SYSTEM_GENERATED
        );
        List<RDFNode> generatedValues = model.listObjectsOfProperty(
                resource,
                generatedProperty
        ).toList();
        if (generatedValues.size() != 1 || !generatedValues.getFirst().isLiteral())
            return false;

        Literal marker = generatedValues.getFirst().asLiteral();
        if (!XSDDatatype.XSDboolean.getURI().equals(marker.getDatatypeURI())
                || !marker.getBoolean())
            return false;

        Property sourceProperty = annotationProperty(
                model,
                OntologyTerms.AnnotationProperties.GENERATED_FROM_REGION
        );
        List<RDFNode> sources = model.listObjectsOfProperty(resource, sourceProperty).toList();
        return sources.size() == 1
                && sources.getFirst().isURIResource()
                && region.getURI().equals(sources.getFirst().asResource().getURI());
    }

    private void markGenerated(OntModel model, Resource resource, Resource region) {
        Property generatedProperty = model.createAnnotationProperty(store.uri(
                OntologyTerms.AnnotationProperties.SYSTEM_GENERATED
        ));
        Property sourceProperty = model.createAnnotationProperty(store.uri(
                OntologyTerms.AnnotationProperties.GENERATED_FROM_REGION
        ));
        resource.addLiteral(generatedProperty, model.createTypedLiteral(true));
        resource.addProperty(sourceProperty, region);
    }

    private Property annotationProperty(OntModel model, String localName) {
        return model.getProperty(store.uri(localName));
    }

    private void replaceGeneratedLabels(
            OntModel model,
            Resource resource,
            String labelBg,
            String labelEn
    ) {
        replaceLocalizedLanguage(model, resource, RDFS.label, labelBg, "bg");
        replaceLocalizedLanguage(model, resource, RDFS.label, labelEn, "en");
    }

    private boolean hasIncorrectManagedParent(
            OntModel model,
            OntClass derived,
            OntClass expectedParent
    ) {
        return managedRegionalParents(model).stream()
                .anyMatch(parent -> !parent.equals(expectedParent)
                        && derived.hasSuperClass(parent, true));
    }

    private void removeIncorrectManagedParents(
            OntModel model,
            OntClass derived,
            OntClass expectedParent
    ) {
        managedRegionalParents(model).stream()
                .filter(parent -> !parent.equals(expectedParent))
                .filter(parent -> derived.hasSuperClass(parent, true))
                .forEach(derived::removeSuperClass);
    }

    private List<OntClass> managedRegionalParents(OntModel model) {
        return List.of(
                requiredClass(model, OntologyTerms.Classes.REGIONAL_EMBROIDERY),
                requiredClass(model, OntologyTerms.Classes.REGIONAL_MOTIF)
        );
    }

    private boolean hasExactLocalizedValue(
            OntModel model,
            Resource resource,
            Property property,
            String expected,
            String language
    ) {
        List<String> values = literals(model, resource, property, language);
        return expected == null ? values.isEmpty() : values.equals(List.of(expected));
    }

    private void replaceLocalizedLanguage(
            OntModel model,
            Resource resource,
            Property property,
            String value,
            String language
    ) {
        List<Statement> existing = model.listStatements(resource, property, (RDFNode) null)
                .filterKeep(statement -> statement.getObject().isLiteral())
                .filterKeep(statement -> language.equalsIgnoreCase(
                        statement.getLiteral().getLanguage()
                ))
                .toList();
        model.remove(existing);
        addLiteral(model, resource, property, value, language);
    }

    private void replaceRestrictionsForProperty(
            OntModel model,
            Resource subject,
            String propertyName,
            Set<String> valueLocalNames,
            String expectedClass
    ) {
        removeRestrictionsForProperty(model, subject, propertyName);
        addRestrictions(model, subject, propertyName, valueLocalNames, expectedClass);
    }

    private void removeRestrictionsForProperty(
            OntModel model,
            Resource subject,
            String propertyName
    ) {
        List<Resource> matching = model.listObjectsOfProperty(subject, RDFS.subClassOf)
                .filterKeep(RDFNode::isAnon)
                .mapWith(RDFNode::asResource)
                .filterKeep(resource -> {
                    Statement property = resource.getProperty(OWL.onProperty);
                    return property != null
                            && property.getObject().isURIResource()
                            && propertyName.equals(property.getResource().getLocalName());
                })
                .toList();
        matching.forEach(restriction -> {
            model.removeAll(subject, RDFS.subClassOf, restriction);
            model.removeAll(restriction, null, (RDFNode) null);
        });
    }

    private OntClass createAvailableClass(OntModel model, String uri) {
        Resource candidate = model.getResource(uri);
        if (model.containsResource(candidate))
            throw error(
                    OntologyEntityException.Reason.ALREADY_EXISTS,
                    "Ontology resource already exists: " + candidate.getLocalName()
            );

        return model.createClass(uri);
    }

    private List<Resource> ownedDerivedRegionTypes(OntModel model, Resource region) {
        List<Resource> result = new ArrayList<>();
        for (DerivedRegionTypeDefinition definition : DERIVED_REGION_TYPES) {
            OntClass candidate = model.getOntClass(derivedRegionTypeUri(region, definition));
            if (candidate != null && isOwnedByRegion(model, candidate, region))
                result.add(candidate);
        }
        return List.copyOf(result);
    }

    private String derivedRegionTypeUri(
            Resource region,
            DerivedRegionTypeDefinition definition
    ) {
        return store.uri(regionStem(region.getLocalName()) + definition.suffix());
    }

    private void removeResource(OntModel model, Resource resource, boolean removeRestrictions) {
        if (removeRestrictions)
            removeManagedRestrictions(model, resource);
        model.removeAll(resource, null, (RDFNode) null);
    }

    private String regionStem(String localName) {
        return localName.endsWith("Region")
                ? localName.substring(0, localName.length() - "Region".length())
                : localName;
    }

    private String prefixed(String prefix, String value) {
        return isBlank(value) ? null : prefix + value.trim();
    }

    private record DerivedRegionTypeDefinition(
            OntologyEntityKind kind,
            String regionProperty,
            String suffix,
            String bgPrefix,
            String enPrefix
    ) {
    }

    @Contract("_, _ -> new")
    private @NonNull OntologyEntityException error(OntologyEntityException.Reason reason, String message) {
        return new OntologyEntityException(reason, message);
    }
}
