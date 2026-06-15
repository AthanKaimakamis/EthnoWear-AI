package fmi.ethnowear.ontology.admin;

import fmi.ethnowear.ontology.OntologyTerms;
import fmi.ethnowear.ontology.jena.JenaOntologyStore;
import org.apache.jena.ontology.Individual;
import org.apache.jena.ontology.OntClass;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.RDF;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class OrnamentOntologyAdminService {

    private static final String SKOS_NS = "http://www.w3.org/2004/02/skos/core#";
    private static final Property SKOS_ALT_LABEL = ResourceFactory.createProperty(SKOS_NS, "altLabel");
    private static final Pattern LOCAL_NAME_PATTERN = Pattern.compile("[A-Za-z][A-Za-z0-9_]*");

    private static final Set<String> ALLOWED_TYPES = Set.of(
            OntologyTerms.Classes.GEOMETRIC_ORNAMENT,
            OntologyTerms.Classes.PLANT_ORNAMENT,
            OntologyTerms.Classes.ANIMAL_ORNAMENT,
            OntologyTerms.Classes.HUMAN_ORNAMENT,
            OntologyTerms.Classes.SYMBOLIC_ORNAMENT
    );

    private final JenaOntologyStore store;

    public OrnamentOntologyAdminService(JenaOntologyStore store) {
        this.store = store;
    }

    public OrnamentDetails create(OrnamentCreateCommand command) {
        validateCommand(command);

        store.write(model -> {
            String ornamentUri = store.uri(command.localName());
            Resource ornamentResource = model.getResource(ornamentUri);
            if (model.containsResource(ornamentResource)) {
                throw new OrnamentAlreadyExistsException(command.localName());
            }

            List<OntClass> types = command.typeLocalNames().stream()
                    .map(type -> requiredOrnamentType(model, type))
                    .toList();
            List<Individual> regions = command.characteristicRegionLocalNames().stream()
                    .map(region -> requiredRegion(model, region))
                    .toList();

            Individual ornament = model.createIndividual(ornamentUri, types.getFirst());
            types.stream().skip(1).forEach(ornament::addRDFType);

            addLocalizedLiteral(model, ornament, RDFS.label, command.labelBg(), "bg");
            addLocalizedLiteral(model, ornament, RDFS.label, command.labelEn(), "en");
            addLocalizedLiterals(model, ornament, SKOS_ALT_LABEL, command.altLabelsBg(), "bg");
            addLocalizedLiterals(model, ornament, SKOS_ALT_LABEL, command.altLabelsEn(), "en");
            addLocalizedLiteral(model, ornament, RDFS.comment, command.commentBg(), "bg");
            addLocalizedLiteral(model, ornament, RDFS.comment, command.commentEn(), "en");

            Property characteristicProperty = model.getProperty(store.uri(
                    OntologyTerms.ObjectProperties.IS_CHARACTERISTIC_ORNAMENT_OF_REGION
            ));
            regions.forEach(region -> ornament.addProperty(characteristicProperty, region));
            return command.localName();
        });

        return get(command.localName()).orElseThrow(() -> new OrnamentNotFoundException(command.localName()));
    }

    public Optional<OrnamentDetails> get(String localName) {
        return store.read(model -> {
            Individual ornament = model.getIndividual(store.uri(localName));
            OntClass ornamentClass = model.getOntClass(store.uri(OntologyTerms.Classes.ORNAMENT));
            if (ornament == null || ornamentClass == null || !ornament.hasOntClass(ornamentClass, false)) {
                return Optional.empty();
            }
            return Optional.of(toDetails(model, ornament));
        });
    }

    public List<OrnamentDetails> list() {
        return store.read(model -> {
            OntClass ornamentClass = model.getOntClass(store.uri(OntologyTerms.Classes.ORNAMENT));
            if (ornamentClass == null) {
                return List.of();
            }
            return model.listIndividuals(ornamentClass)
                    .filterKeep(individual -> individual.getURI() != null && !directAllowedTypes(individual).isEmpty())
                    .mapWith(individual -> toDetails(model, individual))
                    .toList().stream()
                    .sorted((left, right) -> left.localName().compareToIgnoreCase(right.localName()))
                    .toList();
        });
    }

    public OrnamentDetails update(String localName, OrnamentUpdateCommand command) {
        validateLocalName(localName);
        validateUpdateCommand(command);

        store.write(model -> {
            Individual ornament = requiredOrnament(model, localName);
            List<OntClass> types = command.typeLocalNames().stream()
                    .map(type -> requiredOrnamentType(model, type))
                    .toList();
            List<Individual> regions = command.characteristicRegionLocalNames().stream()
                    .map(region -> requiredRegion(model, region))
                    .toList();

            removeManagedTypes(model, ornament);
            types.forEach(ornament::addRDFType);
            replaceLocalizedValues(model, ornament, RDFS.label, command.labelBg(), command.labelEn());
            replaceLocalizedValues(model, ornament, RDFS.comment, command.commentBg(), command.commentEn());
            replaceLocalizedValues(
                    model,
                    ornament,
                    SKOS_ALT_LABEL,
                    command.altLabelsBg(),
                    command.altLabelsEn()
            );

            Property characteristicProperty = characteristicRegionProperty(model);
            model.removeAll(ornament, characteristicProperty, null);
            regions.forEach(region -> ornament.addProperty(characteristicProperty, region));
            return null;
        });

        return get(localName).orElseThrow(() -> new OrnamentNotFoundException(localName));
    }

    public OrnamentDetails addCharacteristicRegion(String localName, String regionLocalName) {
        validateLocalName(localName);
        validateLocalName(regionLocalName);
        store.write(model -> {
            Individual ornament = requiredOrnament(model, localName);
            Individual region = requiredRegion(model, regionLocalName);
            ornament.addProperty(characteristicRegionProperty(model), region);
            return null;
        });
        return get(localName).orElseThrow(() -> new OrnamentNotFoundException(localName));
    }

    public OrnamentDetails removeCharacteristicRegion(String localName, String regionLocalName) {
        validateLocalName(localName);
        validateLocalName(regionLocalName);
        store.write(model -> {
            Individual ornament = requiredOrnament(model, localName);
            Individual region = requiredRegion(model, regionLocalName);
            model.removeAll(ornament, characteristicRegionProperty(model), region);
            return null;
        });
        return get(localName).orElseThrow(() -> new OrnamentNotFoundException(localName));
    }

    public void delete(String localName) {
        store.write(model -> {
            Individual ornament = requiredOrnament(model, localName);
            List<OntologyReference> references = incomingReferences(model, ornament);
            if (!references.isEmpty()) {
                throw new OrnamentInUseException(localName, references);
            }

            model.removeAll(ornament, null, (RDFNode) null);
            return null;
        });
    }

    private void validateCommand(OrnamentCreateCommand command) {
        if (command == null) {
            throw new InvalidOrnamentException("Ornament command is required");
        }
        validateLocalName(command.localName());
        validateValues(command.typeLocalNames(), command.labelBg(), command.labelEn(),
                command.altLabelsBg(), command.altLabelsEn());
    }

    private void validateUpdateCommand(OrnamentUpdateCommand command) {
        if (command == null) {
            throw new InvalidOrnamentException("Ornament update command is required");
        }
        validateValues(command.typeLocalNames(), command.labelBg(), command.labelEn(),
                command.altLabelsBg(), command.altLabelsEn());
    }

    private void validateValues(
            Set<String> typeLocalNames,
            String labelBg,
            String labelEn,
            List<String> altLabelsBg,
            List<String> altLabelsEn
    ) {
        if (typeLocalNames.isEmpty()) {
            throw new InvalidOrnamentException("At least one ornament type is required");
        }
        if (!ALLOWED_TYPES.containsAll(typeLocalNames)) {
            throw new InvalidOrnamentException("Unsupported ornament type");
        }
        if (isBlank(labelBg) && isBlank(labelEn)) {
            throw new InvalidOrnamentException("At least one localized label is required");
        }
        validateValues(altLabelsBg, "Bulgarian alternative labels");
        validateValues(altLabelsEn, "English alternative labels");
    }

    private void validateLocalName(String localName) {
        if (localName == null || !LOCAL_NAME_PATTERN.matcher(localName).matches()) {
            throw new InvalidOrnamentException("Invalid ontology local name: " + localName);
        }
    }

    private OntClass requiredOrnamentType(OntModel model, String localName) {
        if (!ALLOWED_TYPES.contains(localName)) {
            throw new InvalidOrnamentException("Unsupported ornament type: " + localName);
        }
        OntClass type = model.getOntClass(store.uri(localName));
        if (type == null) {
            throw new InvalidOrnamentException("Ornament type does not exist: " + localName);
        }
        return type;
    }

    private Individual requiredRegion(OntModel model, String localName) {
        Individual region = model.getIndividual(store.uri(localName));
        OntClass regionClass = model.getOntClass(store.uri(OntologyTerms.Classes.REGION));
        if (region == null || regionClass == null || !region.hasOntClass(regionClass, false)) {
            throw new InvalidOrnamentException("Region does not exist: " + localName);
        }
        return region;
    }

    private Individual requiredOrnament(OntModel model, String localName) {
        Individual ornament = model.getIndividual(store.uri(localName));
        if (ornament == null || directAllowedTypes(ornament).isEmpty()) {
            throw new OrnamentNotFoundException(localName);
        }
        return ornament;
    }

    private OrnamentDetails toDetails(OntModel model, Individual ornament) {
        Property characteristicProperty = characteristicRegionProperty(model);
        Set<String> regions = new LinkedHashSet<>();
        model.listObjectsOfProperty(ornament, characteristicProperty)
                .filterKeep(RDFNode::isResource)
                .mapWith(RDFNode::asResource)
                .filterKeep(resource -> resource.getLocalName() != null)
                .forEachRemaining(resource -> regions.add(resource.getLocalName()));

        return new OrnamentDetails(
                ornament.getURI(),
                ornament.getLocalName(),
                directAllowedTypes(ornament),
                firstLiteral(model, ornament, RDFS.label, "bg").orElse(null),
                firstLiteral(model, ornament, RDFS.label, "en").orElse(null),
                literals(model, ornament, SKOS_ALT_LABEL, "bg"),
                literals(model, ornament, SKOS_ALT_LABEL, "en"),
                firstLiteral(model, ornament, RDFS.comment, "bg").orElse(null),
                firstLiteral(model, ornament, RDFS.comment, "en").orElse(null),
                Set.copyOf(regions)
        );
    }

    private Property characteristicRegionProperty(OntModel model) {
        return model.getProperty(store.uri(
                OntologyTerms.ObjectProperties.IS_CHARACTERISTIC_ORNAMENT_OF_REGION
        ));
    }

    private void removeManagedTypes(OntModel model, Individual ornament) {
        List<Statement> managedTypes = model.listStatements(ornament, RDF.type, (RDFNode) null)
                .filterKeep(statement -> statement.getObject().isResource())
                .filterKeep(statement -> {
                    String localName = statement.getResource().getLocalName();
                    return localName != null && ALLOWED_TYPES.contains(localName);
                })
                .toList();
        model.remove(managedTypes);
    }

    private void replaceLocalizedValues(
            OntModel model,
            Resource resource,
            Property property,
            String valueBg,
            String valueEn
    ) {
        removeLocalizedValues(model, resource, property, Set.of("bg", "en"));
        addLocalizedLiteral(model, resource, property, valueBg, "bg");
        addLocalizedLiteral(model, resource, property, valueEn, "en");
    }

    private void replaceLocalizedValues(
            OntModel model,
            Resource resource,
            Property property,
            List<String> valuesBg,
            List<String> valuesEn
    ) {
        removeLocalizedValues(model, resource, property, Set.of("bg", "en"));
        addLocalizedLiterals(model, resource, property, valuesBg, "bg");
        addLocalizedLiterals(model, resource, property, valuesEn, "en");
    }

    private void removeLocalizedValues(
            OntModel model,
            Resource resource,
            Property property,
            Set<String> languages
    ) {
        List<Statement> statements = model.listStatements(resource, property, (RDFNode) null)
                .filterKeep(statement -> statement.getObject().isLiteral())
                .filterKeep(statement -> languages.contains(
                        statement.getLiteral().getLanguage().toLowerCase()
                ))
                .toList();
        model.remove(statements);
    }

    private Set<String> directAllowedTypes(Individual ornament) {
        Set<String> types = new LinkedHashSet<>();
        ornament.listRDFTypes(true)
                .filterKeep(resource -> resource.getLocalName() != null)
                .forEachRemaining(resource -> {
                    if (ALLOWED_TYPES.contains(resource.getLocalName())) {
                        types.add(resource.getLocalName());
                    }
                });
        return Set.copyOf(types);
    }

    private List<OntologyReference> incomingReferences(OntModel model, Resource ornament) {
        List<OntologyReference> references = new ArrayList<>();
        StmtIterator statements = model.listStatements(null, null, ornament);
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

    private void addLocalizedLiterals(
            OntModel model,
            Resource resource,
            Property property,
            List<String> values,
            String language
    ) {
        values.forEach(value -> addLocalizedLiteral(model, resource, property, value, language));
    }

    private void addLocalizedLiteral(
            OntModel model,
            Resource resource,
            Property property,
            String value,
            String language
    ) {
        if (!isBlank(value)) {
            resource.addProperty(property, model.createLiteral(value.trim(), language));
        }
    }

    private Optional<String> firstLiteral(
            OntModel model,
            Resource resource,
            Property property,
            String language
    ) {
        return literals(model, resource, property, language).stream().findFirst();
    }

    private List<String> literals(
            OntModel model,
            Resource resource,
            Property property,
            String language
    ) {
        List<String> values = new ArrayList<>();
        StmtIterator statements = model.listStatements(resource, property, (RDFNode) null);
        while (statements.hasNext()) {
            RDFNode value = statements.nextStatement().getObject();
            if (value instanceof Literal literal && language.equalsIgnoreCase(literal.getLanguage())) {
                values.add(literal.getString());
            }
        }
        return List.copyOf(values);
    }

    private void validateValues(List<String> values, String fieldName) {
        if (values.stream().anyMatch(this::isBlank)) {
            throw new InvalidOrnamentException(fieldName + " cannot contain blank values");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
