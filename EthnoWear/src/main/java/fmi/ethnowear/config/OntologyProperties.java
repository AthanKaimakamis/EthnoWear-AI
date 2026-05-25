package fmi.ethnowear.config;

import java.nio.file.Path;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Setter
@Getter
@ConfigurationProperties(prefix = "ethnowear.ontology")
public class OntologyProperties {

    private Path path = Path.of("Ontology/EthnoWear.owx");

    private String namespace = "http://www.semanticweb.org/athan/ontologies/2026/4/ethno-wear#";

}
