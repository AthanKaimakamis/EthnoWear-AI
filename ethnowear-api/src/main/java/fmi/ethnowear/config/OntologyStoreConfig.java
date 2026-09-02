package fmi.ethnowear.config;

import fmi.ethnowear.infrastructure.ontology.jena.JenaOntologyStore;
import fmi.ethnowear.application.port.ontology.admin.OntologyChangeMetadataProvider;
import fmi.ethnowear.application.port.ontology.admin.OntologyVersionRecorder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OntologyStoreConfig {

    @Bean
    JenaOntologyStore jenaOntologyStore(
            OntologyProperties props,
            OntologyVersionRecorder versionRecorder,
            OntologyChangeMetadataProvider metadataProvider
    ) {
        return new JenaOntologyStore(
                props.getPath(),
                props.getNamespace(),
                versionRecorder,
                metadataProvider
        );
    }
}
