package fmi.ethnowear.application.dto.document.query;

public record DocumentSourceInheritanceDetails(
        Long documentId,
        Long defaultSourceReferenceId,
        int inheritedPageCount
) {
}
