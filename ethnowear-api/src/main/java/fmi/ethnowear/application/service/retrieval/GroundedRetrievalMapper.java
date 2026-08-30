package fmi.ethnowear.application.service.retrieval;

import fmi.ethnowear.application.dto.retrieval.GroundedPageCitationDetails;
import fmi.ethnowear.application.dto.retrieval.GroundedPassageDetails;
import fmi.ethnowear.application.dto.retrieval.GroundedSourceCitationDetails;
import fmi.ethnowear.application.port.retrieval.VectorSearchCandidate;
import fmi.ethnowear.domain.model.document.DocumentType;
import fmi.ethnowear.persistence.jpa.entity.KnowledgeChunk;
import fmi.ethnowear.persistence.jpa.entity.Source;
import fmi.ethnowear.persistence.jpa.entity.SourceReference;
import fmi.ethnowear.persistence.jpa.entity.document.Document;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPage;
import fmi.ethnowear.persistence.jpa.entity.document.KnowledgeChunkPage;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class GroundedRetrievalMapper {

    public GroundedPassageDetails toDetails(
            KnowledgeChunk chunk,
            VectorSearchCandidate candidate,
            List<KnowledgeChunkPage> pageLinks
    ) {
        Document document = chunk.getDocument();

        return new GroundedPassageDetails(
                chunk.getId(),
                chunk.getContent(),
                chunk.getLanguage(),
                chunk.getChunkType(),
                document.getId(),
                document.getTitle(),
                pageLinks.stream()
                        .map(link -> pageDetails(chunk, link))
                        .toList(),
                candidate.similarity(),
                document.getProvenanceStatus(),
                chunk.getProvenanceTrustState(),
                chunk.getTranscriptionApprovalState(),
                document.getDocumentType() == DocumentType.STANDALONE_CAPTURE
                        || document.getDocumentType()
                        == DocumentType.UNKNOWN_FRAGMENT_SET
        );
    }

    private GroundedPageCitationDetails pageDetails(
            KnowledgeChunk chunk,
            KnowledgeChunkPage link
    ) {
        DocumentPage page = link.getDocumentPage();
        SourceReference reference = page.getSourceReference();

        if (reference == null)
            reference = chunk.getSourceReference();

        if (reference == null)
            reference = chunk.getDocument().getDefaultSourceReference();

        return new GroundedPageCitationDetails(
                page.getId(),
                page.getPageSequence(),
                page.getPdfPageIndex(),
                link.getCitationPrintedPageNumber() != null
                        ? link.getCitationPrintedPageNumber()
                        : page.getPrintedPageNumber(),
                link.getCitationLabel(),
                sourceDetails(reference, chunk.getDocument())
        );
    }

    private GroundedSourceCitationDetails sourceDetails(
            SourceReference reference,
            Document document
    ) {
        Source source = reference != null
                ? reference.getSource()
                : document.getSource();

        if (source == null && reference == null)
            return null;

        return new GroundedSourceCitationDetails(
                source == null ? null : source.getId(),
                source == null ? document.getTitle() : source.getTitle(),
                source == null ? document.getAuthor() : source.getAuthor(),
                source == null ? document.getPublisher() : source.getPublisher(),
                source == null ? document.getPublicationYear() : source.getYear(),
                reference == null ? null : reference.getId(),
                reference == null ? null : reference.getChapter(),
                reference == null ? null : reference.getPageFrom(),
                reference == null ? null : reference.getPageTo(),
                reference == null ? null : reference.getFigureNumber(),
                reference == null ? null : reference.getSectionTitle(),
                reference == null ? null : reference.getLocator()
        );
    }
}
