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
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class TechniqueOntologyAdminService {

    private static final String SKOS_NS = "http://www.w3.org/2004/02/skos/core#";
    private static final Property SKOS_ALT_LABEL = ResourceFactory.createProperty(SKOS_NS, "altLabel");
    private static final Pattern LOCAL_NAME_PATTERN = Pattern.compile("[A-Za-z][A-Za-z0-9_]*");

    private static final Set<String> ALLOWED_TYPES = Set.of(
            OntologyTerms.Classes.CHAIN_TECHNIQUE,
            OntologyTerms.Classes.CONTOUR_TECHNIQUE,
            OntologyTerms.Classes.CROSS_TECHNIQUE,
            OntologyTerms.Classes.GAITAN_TECHNIQUE,
            OntologyTerms.Classes.OPENWORK_TECHNIQUE,
            OntologyTerms.Classes.POLIGAT_TECHNIQUE,
            OntologyTerms.Classes.SCALLOP_TECHNIQUE,
            OntologyTerms.Classes.SPLIT_TECHNIQUE
    );

    private final JenaOntologyStore store;

    public TechniqueOntologyAdminService(JenaOntologyStore store) {
        this.store = store;
    }

    public TechniqueDetails create(TechniqueCreateCommand command) {
        validateCreateCommand(command);

        store.write(model -> {
            String techniqueUri = store.uri(command.localName());
            Resource techniqueResource = model.getResource(techniqueUri);
            if (model.containsResource(techniqueResource)) {
                throw new TechniqueAlreadyExistsException(command.localName());
            }

            List<OntClass> types = requiredTypes(model, command.typeLocalNames());
            List<Individual> regions = requiredRegions(model, command.characteristicRegionLocalNames());
            Individual technique = model.createIndividual(techniqueUri, types.getFirst());
            types.stream().skip(1).forEach(technique::addRDFType);
            addManagedValues(model, technique, command);
            Property regionProperty = characteristicRegionProperty(model);
            regions.forEach(region -> technique.addProperty(regionProperty, region));
            return null;
        });

        return get(command.localName()).orElseThrow(() -> new TechniqueNotFoundException(command.localName()));
    }

    public Optional<TechniqueDetails> get(String localName) {
        return store.read(model -> {
            Individual technique = model.getIndividual(store.uri(localName));
            OntClass techniqueClass = model.getOntClass(store.uri(OntologyTerms.Classes.TECHNIQUE));
            if (technique == null || techniqueClass == null || !technique.hasOntClass(techniqueClass, false)) {
                return Optional.empty();
            }
            return Optional.of(toDetails(model, technique));
        });
    }

    public List<TechniqueDetails> list() {
        return store.read(model -> {
            OntClass techniqueClass = model.getOntClass(store.uri(OntologyTerms.Classes.TECHNIQUE));
            if (techniqueClass == null) {
                return List.of();
            }
            return model.listIndividuals(techniqueClass)
                    .filterKeep(individual -> individual.getURI() != null && !directAllowedTypes(individual).isEmpty())
                    .mapWith(individual -> toDetails(model, individual))
                    .toList().stream()
                    .sorted((left, right) -> left.localName().compareToIgnoreCase(right.localName()))
                    .toList();
        });
    }

    public TechniqueDetails update(String localName, TechniqueUpdateCommand command) {
        validateLocalName(localName);
        validateUpdateCommand(command);

        store.write(model -> {
            Individual technique = requiredTechnique(model, localName);
            List<OntClass> types = requiredTypes(model, command.typeLocalNames());
            List<Individual> regions = requiredRegions(model, command.characteristicRegionLocalNames());

            removeManagedTypes(model, technique);
            types.forEach(technique::addRDFType);
            replaceLocalizedValues(model, technique, RDFS.label, command.labelBg(), command.labelEn());
            replaceLocalizedValues(model, technique, RDFS.comment, command.commentBg(), command.commentEn());
            replaceLocalizedValues(
                    model,
                    technique,
                    SKOS_ALT_LABEL,
                    command.altLabelsBg(),
                    command.altLabelsEn()
            );

            Property regionProperty = characteristicRegionProperty(model);
            model.removeAll(technique, regionProperty, null);
            regions.forEach(region -> technique.addProperty(regionProperty, region));
            return null;
        });

        return get(localName).orElseThrow(() -> new TechniqueNotFoundException(localName));
    }

    public TechniqueDetails addCharacteristicRegion(String localName, String regionLocalName) {
        validateLocalName(localName);
        validateLocalName(regionLocalName);
        store.write(model -> {
            Individual technique = requiredTechnique(model, localName);
            Individual region = requiredRegion(model, regionLocalName);
            technique.addProperty(characteristicRegionProperty(model), region);
            return null;
        });
        return get(localName).orElseThrow(() -> new TechniqueNotFoundException(localName));
    }

    public TechniqueDetails removeCharacteristicRegion(String localName, String regionLocalName) {
        validateLocalName(localName);
        validateLocalName(regionLocalName);
        store.write(model -> {
            Individual technique = requiredTechnique(model, localName);
            Individual region = requiredRegion(model, regionLocalName);
            model.removeAll(technique, characteristicRegionProperty(model), region);
            return null;
        });
        return get(localName).orElseThrow(() -> new TechniqueNotFoundException(localName));
    }

    public void delete(String localName) {
        validateLocalName(localName);
        store.write(model -> {
            Individual technique = requiredTechnique(model, localName);
            List<OntologyReference> references = incomingReferences(model, technique);
            if (!references.isEmpty()) {
                throw new TechniqueInUseException(localName, references);
            }
            model.removeAll(technique, null, (RDFNode) null);
            return null;
        });
    }

    private void validateCreateCommand(TechniqueCreateCommand command) {
        if (command == null) {
            throw new InvalidTechniqueException("Technique command is required");
        }
        validateLocalName(command.localName());
        validateValues(
                command.typeLocalNames(),
                command.labelBg(),
                command.labelEn(),
                command.altLabelsBg(),
                command.altLabelsEn()
        );
    }

    private void validateUpdateCommand(TechniqueUpdateCommand command) {
        if (command == null) {
            throw new InvalidTechniqueException("Technique update command is required");
        }
        validateValues(
                command.typeLocalNames(),
                command.labelBg(),
                command.labelEn(),
                command.altLabelsBg(),
                command.altLabelsEn()
        );
    }

    private void validateValues(
            Set<String> typeLocalNames,
            String labelBg,
            String labelEn,
            List<String> altLabelsBg,
            List<String> altLabelsEn
    ) {
        if (typeLocalNames.isEmpty()) {
            throw new InvalidTechniqueException("At least one technique type is required");
        }
        if (!ALLOWED_TYPES.containsAll(typeLocalNames)) {
            throw new InvalidTechniqueException("Unsupported technique type");
        }
        if (isBlank(labelBg) && isBlank(labelEn)) {
            throw new InvalidTechniqueException("At least one localized label is required");
        }
        validateLiteralValues(altLabelsBg, "Bulgarian alternative labels");
        validateLiteralValues(altLabelsEn, "English alternative labels");
    }

    private void validateLocalName(String localName) {
        if (localName == null || !LOCAL_NAME_PATTERN.matcher(localName).matches()) {
            throw new InvalidTechniqueException("Invalid ontology local name: " + localName);
        }
    }

    private List<OntClass> requiredTypes(OntModel model, Set<String> localNames) {
        return localNames.stream().map(localName -> requiredType(model, localName)).toList();
    }

    private OntClass requiredType(OntModel model, String localName) {
        if (!ALLOWED_TYPES.contains(localName)) {
            throw new InvalidTechniqueException("Unsupported technique type: " + localName);
        }
        OntClass type = model.getOntClass(store.uri(localName));
        if (type == null) {
            throw new InvalidTechniqueException("Technique type does not exist: " + localName);
        }
        return type;
    }

    private List<Individual> requiredRegions(OntModel model, Set<String> localNames) {
        return localNames.stream().map(localName -> requiredRegion(model, localName)).toList();
    }

    private Individual requiredRegion(OntModel model, String localName) {
        Individual region = model.getIndividual(store.uri(localName));
        OntClass regionClass = model.getOntClass(store.uri(OntologyTerms.Classes.REGION));
        if (region == null || regionClass == null || !region.hasOntClass(regionClass, false)) {
            throw new InvalidTechniqueException("Region does not exist: " + localName);
        }
        return region;
    }

    private Individual requiredTechnique(OntModel model, String localName) {
        Individual technique = model.getIndividual(store.uri(localName));
        if (technique == null || directAllowedTypes(technique).isEmpty()) {
            throw new TechniqueNotFoundException(localName);
        }
        return technique;
    }

    private TechniqueDetails toDetails(OntModel model, Individual technique) {
        Set<String> regions = new LinkedHashSet<>();
        model.listObjectsOfProperty(technique, characteristicRegionProperty(model))
                .filterKeep(RDFNode::isResource)
                .mapWith(RDFNode::asResource)
                .filterKeep(resource -> resource.getLocalName() != null)
                .forEachRemaining(resource -> regions.add(resource.getLocalName()));

        return new TechniqueDetails(
                technique.getURI(),
                technique.getLocalName(),
                directAllowedTypes(technique),
                firstLiteral(model, technique, RDFS.label, "bg").orElse(null),
                firstLiteral(model, technique, RDFS.label, "en").orElse(null),
                literals(model, technique, SKOS_ALT_LABEL, "bg"),
                literals(model, technique, SKOS_ALT_LABEL, "en"),
                firstLiteral(model, technique, RDFS.comment, "bg").orElse(null),
                firstLiteral(model, technique, RDFS.comment, "en").orElse(null),
                Set.copyOf(regions)
        );
    }

    private Set<String> directAllowedTypes(Individual technique) {
        Set<String> types = new LinkedHashSet<>();
        technique.listRDFTypes(true)
                .filterKeep(resource -> resource.getLocalName() != null)
                .forEachRemaining(resource -> {
                    if (ALLOWED_TYPES.contains(resource.getLocalName())) {
                        types.add(resource.getLocalName());
                    }
                });
        return Set.copyOf(types);
    }

    private Property characteristicRegionProperty(OntModel model) {
        return model.getProperty(store.uri(
                OntologyTerms.ObjectProperties.IS_CHARACTERISTIC_TECHNIQUE_OF_REGION
        ));
    }

    private List<OntologyReference> incomingReferences(OntModel model, Resource technique) {
        List<OntologyReference> references = new ArrayList<>();
        StmtIterator statements = model.listStatements(null, null, technique);
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

    private void addManagedValues(OntModel model, Resource technique, TechniqueCreateCommand command) {
        addLocalizedLiteral(model, technique, RDFS.label, command.labelBg(), "bg");
        addLocalizedLiteral(model, technique, RDFS.label, command.labelEn(), "en");
        addLocalizedLiterals(model, technique, SKOS_ALT_LABEL, command.altLabelsBg(), "bg");
        addLocalizedLiterals(model, technique, SKOS_ALT_LABEL, command.altLabelsEn(), "en");
        addLocalizedLiteral(model, technique, RDFS.comment, command.commentBg(), "bg");
        addLocalizedLiteral(model, technique, RDFS.comment, command.commentEn(), "en");
    }

    private void removeManagedTypes(OntModel model, Individual technique) {
        List<Statement> managedTypes = model.listStatements(technique, RDF.type, (RDFNode) null)
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
        removeLocalizedValues(model, resource, property);
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
        removeLocalizedValues(model, resource, property);
        addLocalizedLiterals(model, resource, property, valuesBg, "bg");
        addLocalizedLiterals(model, resource, property, valuesEn, "en");
    }

    private void removeLocalizedValues(OntModel model, Resource resource, Property property) {
        List<Statement> statements = model.listStatements(resource, property, (RDFNode) null)
                .filterKeep(statement -> statement.getObject().isLiteral())
                .filterKeep(statement -> Set.of("bg", "en").contains(
                        statement.getLiteral().getLanguage().toLowerCase()
                ))
                .toList();
        model.remove(statements);
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

    private void validateLiteralValues(List<String> values, String fieldName) {
        if (values.stream().anyMatch(this::isBlank)) {
            throw new InvalidTechniqueException(fieldName + " cannot contain blank values");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
