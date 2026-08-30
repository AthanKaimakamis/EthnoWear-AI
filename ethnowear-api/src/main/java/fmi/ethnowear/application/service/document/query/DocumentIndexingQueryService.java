package fmi.ethnowear.application.service.document.query;

import fmi.ethnowear.application.dto.document.query.DocumentIndexingStatusDetails;
import fmi.ethnowear.application.exception.ResourceNotFoundException;
import fmi.ethnowear.application.service.document.query.mapper.DocumentIndexingStatusMapper;
import fmi.ethnowear.persistence.jpa.entity.document.Document;
import fmi.ethnowear.persistence.jpa.repository.KnowledgeChunkRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static fmi.ethnowear.util.IdentifierUtils.requireId;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DocumentIndexingQueryService {

    private final DocumentRepository documentRepository;
    private final KnowledgeChunkRepository chunkRepository;
    private final DocumentIndexingStatusMapper indexingStatusMapper;

    public DocumentIndexingStatusDetails findByDocumentId(Long documentId) {
        requireId(documentId, "Document");

        Document document = documentRepository
                .findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document", documentId));

        return findByDocuments(List.of(document)).get(documentId);
    }

    public Map<Long, DocumentIndexingStatusDetails> findByDocuments(Collection<Document> documents) {
        validateDocuments(documents);

        if (documents.isEmpty())
            return Map.of();

        List<Long> documentIds = documents.stream()
                .map(Document::getId)
                .distinct()
                .toList();

        return indexingStatusMapper.toDetails(
                documents,
                chunkRepository.countActiveIndexingStates(documentIds)
        );
    }

    private void validateDocuments(Collection<Document> documents) {
        if (documents == null)
            throw new IllegalArgumentException("Documents are required");

        if (documents.stream().anyMatch(Objects::isNull))
            throw new IllegalArgumentException("Documents cannot contain null values");

        documents.forEach(document -> requireId(document.getId(), "Document"));
    }
}
