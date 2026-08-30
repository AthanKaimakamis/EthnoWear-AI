package fmi.ethnowear.config;

import fmi.ethnowear.infrastructure.ontology.jena.JenaOntologyStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OntologyStoreConfig {

    @Bean
    JenaOntologyStore jenaOntologyStore(OntologyProperties props) {
        return new JenaOntologyStore(props.getPath(), props.getNamespace());
    }
}
