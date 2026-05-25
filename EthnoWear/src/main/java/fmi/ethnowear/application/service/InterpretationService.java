package fmi.ethnowear.application.service;

import fmi.ethnowear.ontology.embroidery.EmbroideryOntologyClient;
import org.springframework.stereotype.Service;

@Service
public class InterpretationService {

    private final EmbroideryOntologyClient ontology;

    public InterpretationService(EmbroideryOntologyClient ontology) {
        this.ontology = ontology;
    }


}
