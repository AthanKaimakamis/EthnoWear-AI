package fmi.ethnowear.application.service.document.metadata;

import fmi.ethnowear.application.dto.document.command.metadata.DocumentMetadataUpdateCommand;
import fmi.ethnowear.application.exception.ResourceNotFoundException;
import fmi.ethnowear.persistence.jpa.entity.document.Document;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentRepository;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DocumentMetadataService {

    private final DocumentRepository documentRepository;
    private final Validator validator;

    @Transactional
    public void update(Long documentId, DocumentMetadataUpdateCommand command) {
        if (documentId == null)
            throw new IllegalArgumentException("Document id is required");

        validate(command);

        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document", documentId));

        document.setTitle(command.title().trim());
        document.setAuthor(trimToNull(command.author()));
        document.setPublisher(trimToNull(command.publisher()));
        document.setPublicationYear(command.publicationYear());
        document.setLanguage(trimToNull(command.language()));
        document.setNotes(trimToNull(command.notes()));

        documentRepository.save(document);
    }

    private void validate(DocumentMetadataUpdateCommand command) {
        if (command == null)
            throw new IllegalArgumentException("Document metadata command is required");

        var violations = validator.validate(command);

        if (!violations.isEmpty())
            throw new ConstraintViolationException(violations);
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank())
            return null;

        return value.trim();
    }
}
