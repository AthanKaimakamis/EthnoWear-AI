package fmi.ethnowear.application.dto.ontology.admin;

public record OntologyVersionContent(
        Long id,
        long versionNumber,
        String fileName,
        String content
) {
}
