package fmi.ethnowear.infrastructure.ontology.jena.admin;

import fmi.ethnowear.application.exception.InvalidTechniqueException;
import fmi.ethnowear.application.exception.TechniqueAlreadyExistsException;
import fmi.ethnowear.application.exception.TechniqueInUseException;
import fmi.ethnowear.application.exception.TechniqueNotFoundException;
import fmi.ethnowear.domain.constant.ontology.OntologyTerms;
import fmi.ethnowear.application.dto.ontology.admin.TechniqueCreateCommand;
import fmi.ethnowear.application.dto.ontology.admin.TechniqueUpdateCommand;
import fmi.ethnowear.domain.model.ontology.OntologyReference;
import fmi.ethnowear.application.dto.ontology.admin.TechniqueDetails;
import fmi.ethnowear.application.port.ontology.admin.TechniqueOntologyAdminPort;
import fmi.ethnowear.infrastructure.ontology.jena.JenaOntologyStore;
import fmi.ethnowear.util.TextUtils;
import org.apache.jena.ontology.Individual;
import org.apache.jena.ontology.OntClass;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDFS;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Unmodifiable;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import static fmi.ethnowear.util.TextUtils.isBlank;

@Service
public class TechniqueOntologyAdminService implements TechniqueOntologyAdminPort {

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
    private final OntologyAdminModelSupport modelSupport;

    public TechniqueOntologyAdminService(
            JenaOntologyStore store,
            OntologyAdminModelSupport modelSupport
    ) {
        this.store = store;
        this.modelSupport = modelSupport;
    }

    public TechniqueDetails create(TechniqueCreateCommand command) {
        validateCreateCommand(command);

        store.write(model -> {
            String techniqueUri = store.uri(command.localName());
            Resource techniqueResource = model.getResource(techniqueUri);

            if (model.containsResource(techniqueResource))
                throw new TechniqueAlreadyExistsException(command.localName());

            List<OntClass> types = requiredTypes(model, command.typeLocalNames());
            List<Individual> regions = requiredRegions(model, command.characteristicRegionLocalNames());
            Individual technique = model.createIndividual(techniqueUri, types.getFirst());
            types.stream().skip(1).forEach(technique::addRDFType);
            modelSupport.addManagedLocalizedValues(
                    model,
                    technique,
                    command.labelBg(),
                    command.labelEn(),
                    command.altLabelsBg(),
                    command.altLabelsEn(),
                    command.commentBg(),
                    command.commentEn()
            );
            Property regionProperty = characteristicRegionProperty(model);
            regions.forEach(region -> technique.addProperty(regionProperty, region));

            return null;
        });

        return get(command.localName()).orElseThrow(() -> new TechniqueNotFoundException(command.localName()));
    }

    public Optional<TechniqueDetails> get(String localName) {
        return modelSupport.findIndividual(
                localName,
                OntologyTerms.Classes.TECHNIQUE,
                this::toDetails
        );
    }

    public List<TechniqueDetails> list() {
        return modelSupport.listIndividuals(
                OntologyTerms.Classes.TECHNIQUE,
                ALLOWED_TYPES,
                this::toDetails,
                TechniqueDetails::localName
        );
    }

    public TechniqueDetails update(String localName, TechniqueUpdateCommand command) {
        validateLocalName(localName);
        validateUpdateCommand(command);

        store.write(model -> {
            Individual technique = requiredTechnique(model, localName);
            List<OntClass> types = requiredTypes(model, command.typeLocalNames());
            List<Individual> regions = requiredRegions(model, command.characteristicRegionLocalNames());

            modelSupport.removeManagedTypes(model, technique, ALLOWED_TYPES);
            types.forEach(technique::addRDFType);
            modelSupport.replaceLocalizedValues(
                    model,
                    technique,
                    RDFS.label,
                    command.labelBg(),
                    command.labelEn()
            );
            modelSupport.replaceLocalizedValues(
                    model,
                    technique,
                    RDFS.comment,
                    command.commentBg(),
                    command.commentEn()
            );
            modelSupport.replaceLocalizedValues(
                    model,
                    technique,
                    OntologyAdminModelSupport.SKOS_ALT_LABEL,
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
            List<OntologyReference> references = modelSupport.incomingReferences(model, technique);

            if (!references.isEmpty())
                throw new TechniqueInUseException(localName, references);

            model.removeAll(technique, null, null);
            return null;
        });
    }

    private void validateCreateCommand(TechniqueCreateCommand command) {
        if (command == null)
            throw new InvalidTechniqueException("Technique command is required");

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
        if (command == null)
            throw new InvalidTechniqueException("Technique update command is required");

        validateValues(
                command.typeLocalNames(),
                command.labelBg(),
                command.labelEn(),
                command.altLabelsBg(),
                command.altLabelsEn()
        );
    }

    private void validateValues(
            @NonNull Set<String> typeLocalNames,
            String labelBg,
            String labelEn,
            List<String> altLabelsBg,
            List<String> altLabelsEn
    ) {
        if (typeLocalNames.isEmpty())
            throw new InvalidTechniqueException("At least one technique type is required");

        if (!ALLOWED_TYPES.containsAll(typeLocalNames))
            throw new InvalidTechniqueException("Unsupported technique type");

        if (isBlank(labelBg) && isBlank(labelEn))
            throw new InvalidTechniqueException("At least one localized label is required");

        validateLiteralValues(altLabelsBg, "Bulgarian alternative labels");
        validateLiteralValues(altLabelsEn, "English alternative labels");
    }

    private void validateLocalName(String localName) {
        if (localName == null || !LOCAL_NAME_PATTERN.matcher(localName).matches())
            throw new InvalidTechniqueException("Invalid ontology local name: " + localName);
    }

    private @NonNull @Unmodifiable List<OntClass> requiredTypes(OntModel model, @NonNull Set<String> localNames) {
        return localNames.stream().map(localName -> requiredType(model, localName)).toList();
    }

    private @NonNull OntClass requiredType(OntModel model, String localName) {
        if (!ALLOWED_TYPES.contains(localName))
            throw new InvalidTechniqueException("Unsupported technique type: " + localName);

        OntClass type = model.getOntClass(store.uri(localName));

        if (type == null)
            throw new InvalidTechniqueException("Technique type does not exist: " + localName);

        return type;
    }

    private @NonNull @Unmodifiable List<Individual> requiredRegions(OntModel model, @NonNull Set<String> localNames) {
        return localNames.stream().map(localName -> requiredRegion(model, localName)).toList();
    }

    private @NonNull Individual requiredRegion(@NonNull OntModel model, String localName) {
        return modelSupport.individualOfType(
                        model,
                        localName,
                        OntologyTerms.Classes.REGION
                )
                .orElseThrow(() -> new InvalidTechniqueException(
                        "Region does not exist: " + localName
                ));
    }

    private @NonNull Individual requiredTechnique(@NonNull OntModel model, String localName) {
        Individual technique = model.getIndividual(store.uri(localName));

        if (technique == null || modelSupport.directTypes(technique, ALLOWED_TYPES).isEmpty())
            throw new TechniqueNotFoundException(localName);

        return technique;
    }

    @Contract("_, _ -> new")
    private @NonNull TechniqueDetails toDetails(OntModel model, @NonNull Individual technique) {
        return new TechniqueDetails(
                technique.getURI(),
                technique.getLocalName(),
                modelSupport.directTypes(technique, ALLOWED_TYPES),
                modelSupport.firstLiteral(model, technique, RDFS.label, "bg").orElse(null),
                modelSupport.firstLiteral(model, technique, RDFS.label, "en").orElse(null),
                modelSupport.literals(model, technique, OntologyAdminModelSupport.SKOS_ALT_LABEL, "bg"),
                modelSupport.literals(model, technique, OntologyAdminModelSupport.SKOS_ALT_LABEL, "en"),
                modelSupport.firstLiteral(model, technique, RDFS.comment, "bg").orElse(null),
                modelSupport.firstLiteral(model, technique, RDFS.comment, "en").orElse(null),
                modelSupport.objectLocalNames(model, technique, characteristicRegionProperty(model))
        );
    }

    private Property characteristicRegionProperty(@NonNull OntModel model) {
        return model.getProperty(store.uri(OntologyTerms.ObjectProperties.IS_CHARACTERISTIC_TECHNIQUE_OF_REGION));
    }

    private void validateLiteralValues(@NonNull List<String> values, String fieldName) {
        if (values.stream().anyMatch(TextUtils::isBlank))
            throw new InvalidTechniqueException(fieldName + " cannot contain blank values");
    }
}
