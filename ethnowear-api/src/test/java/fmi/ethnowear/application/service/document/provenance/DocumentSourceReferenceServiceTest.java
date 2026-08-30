package fmi.ethnowear.application.service.document.provenance;

import fmi.ethnowear.application.dto.document.command.provenance.DocumentDefaultSourceReferenceCommand;
import fmi.ethnowear.application.service.document.review.DocumentPageChunkInvalidator;
import fmi.ethnowear.application.service.document.upload.DocumentPageProvenanceRecorder;
import fmi.ethnowear.application.service.document.upload.DocumentUploadReferenceResolver;
import fmi.ethnowear.domain.model.document.provenance.ProvenanceStatus;
import fmi.ethnowear.domain.model.document.provenance.ProvenanceTrustState;
import fmi.ethnowear.persistence.jpa.entity.Source;
import fmi.ethnowear.persistence.jpa.entity.SourceReference;
import fmi.ethnowear.persistence.jpa.entity.document.Document;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPage;
import fmi.ethnowear.persistence.jpa.repository.SourceReferenceRepository;
import fmi.ethnowear.persistence.jpa.repository.SourceRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentRepository;
import fmi.ethnowear.testutil.EntityTestUtils;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static fmi.ethnowear.support.RepositoryTestProxies.proxy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class DocumentSourceReferenceServiceTest {

    @Test
    void setsDefaultReferenceAndInheritsOnlyIntoUnassignedPages() {
        Source source = entity(new Source(), 3L);
        SourceReference sourceReference = entity(new SourceReference(), 5L);
        sourceReference.setSource(source);
        Document document = entity(new Document(), 7L);
        document.setSource(source);
        document.setProvenanceStatus(ProvenanceStatus.KNOWN_SOURCE);
        document.setProvenanceTrustState(ProvenanceTrustState.VERIFIED);
        DocumentPage page = entity(new DocumentPage(), 11L);
        page.setDocument(document);
        DocumentRepository documents = proxy(
                DocumentRepository.class,
                (ignored, method, arguments) -> switch (method.getName()) {
                    case "findById" -> Optional.of(document);
                    case "save" -> arguments[0];
                    default -> throw new AssertionError(
                            "Unexpected document call: " + method.getName()
                    );
                }
        );
        DocumentPageRepository pages = proxy(
                DocumentPageRepository.class,
                (ignored, method, arguments) -> switch (method.getName()) {
                    case "findByDocument_IdAndSourceReferenceIsNullOrderByPageSequenceAsc" -> List.of(page);
                    case "saveAll" -> arguments[0];
                    default -> throw new AssertionError(
                            "Unexpected page call: " + method.getName()
                    );
                }
        );
        SourceReferenceRepository references = proxy(
                SourceReferenceRepository.class,
                (ignored, method, arguments) -> {
                    if(method.getName().equals("findById"))
                        return Optional.of(sourceReference);

                    throw new AssertionError(
                            "Unexpected reference call: " + method.getName()
                    );
                }
        );
        SourceRepository sources = mock(SourceRepository.class);
        DocumentPageProvenanceRecorder recorder = mock(
                DocumentPageProvenanceRecorder.class
        );
        DocumentPageChunkInvalidator invalidator = mock(
                DocumentPageChunkInvalidator.class
        );
        DocumentSourceReferenceService service = new DocumentSourceReferenceService(
                documents,
                pages,
                new DocumentUploadReferenceResolver(sources, references),
                recorder,
                invalidator,
                fmi.ethnowear.support.ManagementEventTestSupport.events()
        );

        var result = service.setDefault(
                7L,
                new DocumentDefaultSourceReferenceCommand(
                        5L,
                        true,
                        "Inherited from the scanned book"
                ),
                "curator"
        );

        assertSame(sourceReference, document.getDefaultSourceReference());
        assertSame(sourceReference, page.getSourceReference());
        assertEquals(ProvenanceStatus.KNOWN_SOURCE, page.getProvenanceStatus());
        assertEquals(ProvenanceTrustState.VERIFIED, page.getProvenanceTrustState());
        assertEquals(1, result.inheritedPageCount());
        verify(invalidator).invalidate(page);
        verify(recorder).recordInitial(
                page,
                sourceReference,
                ProvenanceStatus.KNOWN_SOURCE,
                ProvenanceTrustState.VERIFIED,
                "curator",
                "Inherited from the scanned book"
        );
    }

    private <T extends fmi.ethnowear.persistence.jpa.entity.AppendOnlyEntity> T entity(
            T entity,
            Long id
    ) {
        EntityTestUtils.setId(entity, id);
        return entity;
    }
}
