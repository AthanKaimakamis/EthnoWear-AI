package fmi.ethnowear.application.service.document.query;

import fmi.ethnowear.application.exception.ResourceNotFoundException;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import static fmi.ethnowear.util.IdentifierUtils.requireId;

@Component
@RequiredArgsConstructor
public class DocumentQueryGuard {

    private final DocumentRepository documentRepository;
    private final DocumentPageRepository pageRepository;

    public void requireDocument(Long documentId) {
        requireId(documentId, "Document");

        if (!documentRepository.existsById(documentId))
            throw new ResourceNotFoundException("Document", documentId);
    }

    public void requirePage(Long documentId, Long pageId) {
        requireId(documentId, "Document");
        requireId(pageId, "Document page");

        if (!pageRepository.existsByIdAndDocument_Id(pageId, documentId))
            throw new ResourceNotFoundException("Document page", pageId);
    }
}
