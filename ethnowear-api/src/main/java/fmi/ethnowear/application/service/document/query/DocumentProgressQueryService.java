package fmi.ethnowear.application.service.document.query;

import fmi.ethnowear.application.dto.document.query.DocumentProgressDetails;
import fmi.ethnowear.application.service.document.query.mapper.DocumentProgressMapper;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static fmi.ethnowear.util.IdentifierUtils.requireId;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DocumentProgressQueryService {

    private final DocumentQueryGuard queryGuard;
    private final DocumentPageRepository pageRepository;
    private final DocumentProgressMapper progressMapper;

    public DocumentProgressDetails findByDocumentId(Long documentId) {
        queryGuard.requireDocument(documentId);

        return findByDocumentIds(List.of(documentId)).get(documentId);
    }

    public Map<Long, DocumentProgressDetails> findByDocumentIds(Collection<Long> documentIds) {
        validateDocumentIds(documentIds);

        if (documentIds.isEmpty())
            return Map.of();

        return progressMapper.toDetails(
                documentIds,
                pageRepository.countProcessingStates(documentIds),
                pageRepository.countReviewStates(documentIds),
                pageRepository.countTranscriptionApprovalStates(documentIds),
                pageRepository.countIndexingStates(documentIds)
        );
    }

    private void validateDocumentIds(Collection<Long> documentIds) {
        if (documentIds == null)
            throw new IllegalArgumentException("Document ids are required");

        documentIds.forEach(documentId -> requireId(documentId, "Document"));
    }
}
