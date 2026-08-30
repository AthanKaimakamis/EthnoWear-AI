package fmi.ethnowear.config;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ethnowear.ontology")
public class OntologyProperties {

    private Path path = Path.of("Ontology/EthnoWear.owx");

    private String namespace = "http://www.semanticweb.org/athan/ontologies/2026/4/ethno-wear#";

    public Path getPath() {
        return path;
    }

    public void setPath(Path path) {
        this.path = path;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }
}
