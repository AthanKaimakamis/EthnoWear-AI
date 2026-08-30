package fmi.ethnowear.application.service.document.chunk;

import fmi.ethnowear.application.dto.document.query.chunk.GeneratedChunkCitationDetails;
import fmi.ethnowear.application.dto.document.query.chunk.GeneratedKnowledgeChunkDetails;
import fmi.ethnowear.application.dto.document.query.history.DocumentProcessingJobDetails;
import fmi.ethnowear.application.service.document.query.DocumentQueryGuard;
import fmi.ethnowear.application.service.document.query.mapper.DocumentHistoryMapper;
import fmi.ethnowear.domain.model.document.processing.JobType;
import fmi.ethnowear.persistence.jpa.entity.document.KnowledgeChunkPage;
import fmi.ethnowear.persistence.jpa.repository.KnowledgeChunkRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentProcessingJobRepository;
import fmi.ethnowear.persistence.jpa.repository.document.KnowledgeChunkPageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static fmi.ethnowear.util.PageableUtils.boundedUnsorted;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DocumentChunkQueryService {

    private static final int MAX_PAGE_SIZE = 100;

    private final DocumentQueryGuard queryGuard;
    private final KnowledgeChunkRepository chunkRepository;
    private final KnowledgeChunkPageRepository chunkPageRepository;
    private final DocumentProcessingJobRepository jobRepository;
    private final DocumentHistoryMapper historyMapper;

    public Page<GeneratedKnowledgeChunkDetails> findGeneratedChunks(
            Long documentId,
            Pageable pageable
    ) {
        queryGuard.requireDocument(documentId);
        Pageable boundedRequest = boundedUnsorted(
                pageable,
                MAX_PAGE_SIZE,
                "Generated chunks"
        );
        Pageable bounded = PageRequest.of(
                boundedRequest.getPageNumber(),
                boundedRequest.getPageSize(),
                Sort.by(
                        Sort.Order.desc("createdAt"),
                        Sort.Order.asc("chunkOrdinal")
                )
        );
        var chunks = chunkRepository
                .findByDocument_IdAndGenerationInputHashIsNotNull(
                        documentId,
                        bounded
                );
        List<Long> chunkIds = chunks.stream().map(chunk -> chunk.getId()).toList();
        Map<Long, List<KnowledgeChunkPage>> links = chunkIds.isEmpty()
                ? Map.of()
                : chunkPageRepository
                .findByKnowledgeChunk_IdInOrderByKnowledgeChunk_IdAscPageOrderAsc(
                        chunkIds
                )
                .stream()
                .collect(Collectors.groupingBy(
                        link -> link.getKnowledgeChunk().getId()
                ));

        return new PageImpl<>(
                chunks.stream()
                        .map(chunk -> new GeneratedKnowledgeChunkDetails(
                                chunk.getId(),
                                chunk.getDocument().getId(),
                                chunk.getSourceReference() == null
                                        ? null
                                        : chunk.getSourceReference().getId(),
                                chunk.getChunkType(),
                                chunk.getLanguage(),
                                chunk.getContent(),
                                chunk.getSourceTextType(),
                                chunk.getChunkOrdinal(),
                                chunk.getChunkingStrategy(),
                                chunk.getChunkingVersion(),
                                chunk.getReviewState(),
                                chunk.getTranscriptionApprovalState(),
                                chunk.getProvenanceTrustState(),
                                chunk.getIndexingState(),
                                chunk.getSupersededBy() == null
                                        ? null
                                        : chunk.getSupersededBy().getId(),
                                chunk.getSupersededBy() == null,
                                chunk.getCreatedAt(),
                                links.getOrDefault(chunk.getId(), List.of())
                                        .stream()
                                        .map(this::toCitation)
                                        .toList()
                        ))
                        .toList(),
                bounded,
                chunks.getTotalElements()
        );
    }

    public Page<DocumentProcessingJobDetails> findGenerationJobs(
            Long documentId,
            Pageable pageable
    ) {
        queryGuard.requireDocument(documentId);

        return jobRepository.findDocumentJobsByType(
                        documentId,
                        JobType.CHUNK_GENERATION,
                        boundedUnsorted(
                                pageable,
                                MAX_PAGE_SIZE,
                                "Chunk-generation jobs"
                        )
                )
                .map(historyMapper::toDetails);
    }

    private GeneratedChunkCitationDetails toCitation(
            KnowledgeChunkPage link
    ) {
        return new GeneratedChunkCitationDetails(
                link.getDocumentPage().getId(),
                link.getPageOrder(),
                link.getStartCharOffset(),
                link.getEndCharOffset(),
                link.isStartsOnPage(),
                link.isEndsOnPage(),
                link.getCitationPrintedPageNumber(),
                link.getCitationPdfPageIndex(),
                link.getCitationLabel()
        );
    }
}
