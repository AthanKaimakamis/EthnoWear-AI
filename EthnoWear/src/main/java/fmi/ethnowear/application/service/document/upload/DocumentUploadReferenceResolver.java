package fmi.ethnowear.application.service.document.upload;

import fmi.ethnowear.application.exception.ResourceNotFoundException;
import fmi.ethnowear.persistence.jpa.entity.Source;
import fmi.ethnowear.persistence.jpa.entity.SourceReference;
import fmi.ethnowear.persistence.jpa.entity.document.Document;
import fmi.ethnowear.persistence.jpa.repository.SourceReferenceRepository;
import fmi.ethnowear.persistence.jpa.repository.SourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DocumentUploadReferenceResolver {

    private final SourceRepository sourceRepository;
    private final SourceReferenceRepository sourceReferenceRepository;

    public Source source(Long sourceId) {
        if(sourceId == null)
            return null;

        return sourceRepository
                .findById(sourceId)
                .orElseThrow(() -> new ResourceNotFoundException("Source", sourceId));

    }

    public SourceReference sourceReference(Long sourceReferenceId) {
        if(sourceReferenceId == null)
            return null;

        return sourceReferenceRepository
                .findById(sourceReferenceId)
                .orElseThrow(() -> new ResourceNotFoundException("Source reference", sourceReferenceId));
    }

    public void requireDocumentSource(Document document, SourceReference sourceReference) {
        if(sourceReference == null)
            return;

        Source documentSource = document.getSource();
        Source referenceSource = sourceReference.getSource();

        if(documentSource == null
         || !documentSource.getId().equals(referenceSource.getId()))
            throw new IllegalArgumentException("Source reference does not belong to the document source");
    }
}
