package fmi.ethnowear.ontology.admin;

import fmi.ethnowear.application.exceptions.InvalidOrnamentException;
import fmi.ethnowear.application.exceptions.OrnamentAlreadyExistsException;
import fmi.ethnowear.application.exceptions.OrnamentInUseException;
import fmi.ethnowear.application.exceptions.OrnamentNotFoundException;
import fmi.ethnowear.ontology.OntologyTerms;
import fmi.ethnowear.ontology.admin.command.OrnamentCreateCommand;
import fmi.ethnowear.ontology.admin.command.OrnamentUpdateCommand;
import fmi.ethnowear.ontology.admin.model.OntologyReference;
import fmi.ethnowear.ontology.admin.model.OrnamentDetails;
import fmi.ethnowear.ontology.jena.JenaOntologyStore;
import fmi.ethnowear.util.TextUtils;
import org.apache.jena.ontology.Individual;
import org.apache.jena.ontology.OntClass;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDFS;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import static fmi.ethnowear.util.TextUtils.isBlank;

@Service
public class OrnamentOntologyAdminService {

    private static final Pattern LOCAL_NAME_PATTERN = Pattern.compile("[A-Za-z][A-Za-z0-9_]*");

    private static final Set<String> ALLOWED_TYPES = Set.of(
            OntologyTerms.Classes.GEOMETRIC_ORNAMENT,
            OntologyTerms.Classes.PLANT_ORNAMENT,
            OntologyTerms.Classes.ANIMAL_ORNAMENT,
            OntologyTerms.Classes.HUMAN_ORNAMENT,
            OntologyTerms.Classes.SYMBOLIC_ORNAMENT
    );

    private final JenaOntologyStore store;
    private final OntologyAdminModelSupport modelSupport;

    public OrnamentOntologyAdminService(
            JenaOntologyStore store,
            OntologyAdminModelSupport modelSupport
    ) {
        this.store = store;
        this.modelSupport = modelSupport;
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

            modelSupport.addManagedLocalizedValues(
                    model,
                    ornament,
                    command.labelBg(),
                    command.labelEn(),
                    command.altLabelsBg(),
                    command.altLabelsEn(),
                    command.commentBg(),
                    command.commentEn()
            );

            Property characteristicProperty = model.getProperty(store.uri(
                    OntologyTerms.ObjectProperties.IS_CHARACTERISTIC_ORNAMENT_OF_REGION
            ));
            regions.forEach(region -> ornament.addProperty(characteristicProperty, region));
            return command.localName();
        });

        return get(command.localName()).orElseThrow(() -> new OrnamentNotFoundException(command.localName()));
    }

    public Optional<OrnamentDetails> get(String localName) {
        return modelSupport.findIndividual(
                localName,
                OntologyTerms.Classes.ORNAMENT,
                this::toDetails
        );
    }

    public List<OrnamentDetails> list() {
        return modelSupport.listIndividuals(
                OntologyTerms.Classes.ORNAMENT,
                ALLOWED_TYPES,
                this::toDetails,
                OrnamentDetails::localName
        );
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

            modelSupport.removeManagedTypes(model, ornament, ALLOWED_TYPES);
            types.forEach(ornament::addRDFType);
            modelSupport.replaceLocalizedValues(
                    model,
                    ornament,
                    RDFS.label,
                    command.labelBg(),
                    command.labelEn()
            );
            modelSupport.replaceLocalizedValues(
                    model,
                    ornament,
                    RDFS.comment,
                    command.commentBg(),
                    command.commentEn()
            );
            modelSupport.replaceLocalizedValues(
                    model,
                    ornament,
                    OntologyAdminModelSupport.SKOS_ALT_LABEL,
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
        validateLocalName(localName);
        store.write(model -> {
            Individual ornament = requiredOrnament(model, localName);
            List<OntologyReference> references = modelSupport.incomingReferences(model, ornament);

            if (!references.isEmpty())
                throw new OrnamentInUseException(localName, references);

            model.removeAll(ornament, null, null);
            return null;
        });
    }

    private void validateCommand(OrnamentCreateCommand command) {
        if (command == null)
            throw new InvalidOrnamentException("Ornament command is required");

        validateLocalName(command.localName());
        validateValues(command.typeLocalNames(), command.labelBg(), command.labelEn(),
                command.altLabelsBg(), command.altLabelsEn());
    }

    private void validateUpdateCommand(OrnamentUpdateCommand command) {
        if (command == null)
            throw new InvalidOrnamentException("Ornament update command is required");

        validateValues(command.typeLocalNames(), command.labelBg(), command.labelEn(),
                command.altLabelsBg(), command.altLabelsEn());
    }

    private void validateValues(
            @NonNull Set<String> typeLocalNames,
            String labelBg,
            String labelEn,
            List<String> altLabelsBg,
            List<String> altLabelsEn
    ) {
        if (typeLocalNames.isEmpty())
            throw new InvalidOrnamentException("At least one ornament type is required");

        if (!ALLOWED_TYPES.containsAll(typeLocalNames))
            throw new InvalidOrnamentException("Unsupported ornament type");

        if (isBlank(labelBg) && isBlank(labelEn))
            throw new InvalidOrnamentException("At least one localized label is required");

        validateValues(altLabelsBg, "Bulgarian alternative labels");
        validateValues(altLabelsEn, "English alternative labels");
    }

    private void validateLocalName(String localName) {
        if (localName == null || !LOCAL_NAME_PATTERN.matcher(localName).matches())
            throw new InvalidOrnamentException("Invalid ontology local name: " + localName);
    }

    private @NonNull OntClass requiredOrnamentType(OntModel model, String localName) {
        if (!ALLOWED_TYPES.contains(localName))
            throw new InvalidOrnamentException("Unsupported ornament type: " + localName);

        OntClass type = model.getOntClass(store.uri(localName));

        if (type == null)
            throw new InvalidOrnamentException("Ornament type does not exist: " + localName);

        return type;
    }

    private @NonNull Individual requiredRegion(OntModel model, String localName) {
        return modelSupport
                .individualOfType(model, localName, OntologyTerms.Classes.REGION)
                .orElseThrow(() -> new InvalidOrnamentException("Region does not exist: " + localName));
    }

    private @NonNull Individual requiredOrnament(@NonNull OntModel model, String localName) {
        Individual ornament = model.getIndividual(store.uri(localName));

        if (ornament == null || modelSupport.directTypes(ornament, ALLOWED_TYPES).isEmpty())
            throw new OrnamentNotFoundException(localName);

        return ornament;
    }

    @Contract("_, _ -> new")
    private @NonNull OrnamentDetails toDetails(OntModel model, @NonNull Individual ornament) {
        return new OrnamentDetails(
                ornament.getURI(),
                ornament.getLocalName(),
                modelSupport.directTypes(ornament, ALLOWED_TYPES),
                modelSupport.firstLiteral(model, ornament, RDFS.label, "bg").orElse(null),
                modelSupport.firstLiteral(model, ornament, RDFS.label, "en").orElse(null),
                modelSupport.literals(model, ornament, OntologyAdminModelSupport.SKOS_ALT_LABEL, "bg"),
                modelSupport.literals(model, ornament, OntologyAdminModelSupport.SKOS_ALT_LABEL, "en"),
                modelSupport.firstLiteral(model, ornament, RDFS.comment, "bg").orElse(null),
                modelSupport.firstLiteral(model, ornament, RDFS.comment, "en").orElse(null),
                modelSupport.objectLocalNames(model, ornament, characteristicRegionProperty(model))
        );
    }

    private Property characteristicRegionProperty(@NonNull OntModel model) {
        return model.getProperty(store.uri(OntologyTerms.ObjectProperties.IS_CHARACTERISTIC_ORNAMENT_OF_REGION));
    }

    private void validateValues(@NonNull List<String> values, String fieldName) {
        if (values.stream().anyMatch(TextUtils::isBlank))
            throw new InvalidOrnamentException(fieldName + " cannot contain blank values");
    }
}
