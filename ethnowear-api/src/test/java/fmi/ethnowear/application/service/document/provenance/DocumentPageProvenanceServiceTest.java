package fmi.ethnowear.application.service.document.provenance;

import fmi.ethnowear.application.dto.document.command.provenance.CanonicalPageLinkCommand;
import fmi.ethnowear.application.dto.document.command.provenance.PageProvenanceTrustChangeCommand;
import fmi.ethnowear.application.dto.document.command.provenance.PageSourceProvenanceChangeCommand;
import fmi.ethnowear.application.exception.InvalidDocumentPageProvenanceTransitionException;
import fmi.ethnowear.application.service.document.query.mapper.DocumentHistoryMapper;
import fmi.ethnowear.domain.model.document.EvidenceState;
import fmi.ethnowear.domain.model.document.provenance.ProvenanceEventType;
import fmi.ethnowear.domain.model.document.provenance.ProvenanceStatus;
import fmi.ethnowear.domain.model.document.provenance.ProvenanceTrustState;
import fmi.ethnowear.persistence.jpa.entity.SourceReference;
import fmi.ethnowear.persistence.jpa.entity.document.Document;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPage;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPageProvenanceEvent;
import fmi.ethnowear.persistence.jpa.repository.SourceReferenceRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageProvenanceEventRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageRepository;
import fmi.ethnowear.testutil.EntityTestUtils;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static fmi.ethnowear.support.RepositoryTestProxies.proxy;
import static org.junit.jupiter.api.Assertions.*;

class DocumentPageProvenanceServiceTest {

    @Test
    void identifiesSourceAndRecordsPreviousAndNewProvenance() {
        DocumentPage page = page(11L);
        SourceReference sourceReference = entity(new SourceReference(), 21L);
        List<DocumentPageProvenanceEvent> events = new ArrayList<>();

        var result = service(
                Map.of(11L, page),
                Map.of(21L, sourceReference),
                events
        ).changeSource(
                11L,
                new PageSourceProvenanceChangeCommand(
                        21L,
                        ProvenanceStatus.KNOWN_SOURCE,
                        ProvenanceTrustState.TRUSTED,
                        "Identified from the catalogue",
                        "Bibliographic source confirmed"
                ),
                "curator"
        );

        assertEquals(ProvenanceEventType.SOURCE_IDENTIFIED, result.eventType());
        assertNull(result.previousSourceReferenceId());
        assertEquals(21L, result.newSourceReferenceId());
        assertEquals(ProvenanceStatus.UNKNOWN_SOURCE, result.previousProvenanceStatus());
        assertEquals(ProvenanceStatus.KNOWN_SOURCE, result.newProvenanceStatus());
        assertEquals(ProvenanceTrustState.UNKNOWN, result.previousTrustState());
        assertEquals(ProvenanceTrustState.TRUSTED, result.newTrustState());
        assertSame(sourceReference, page.getSourceReference());
        assertEquals("curator", page.getProvenanceReviewedBy());
        assertNotNull(page.getProvenanceReviewedAt());
        assertEquals(1, events.size());
    }

    @Test
    void rejectsTrustedUnknownProvenance() {
        DocumentPage page = page(11L);

        assertThrows(
                IllegalArgumentException.class,
                () -> service(
                        Map.of(11L, page),
                        Map.of(),
                        new ArrayList<>()
                ).changeTrust(
                        11L,
                        new PageProvenanceTrustChangeCommand(
                                ProvenanceTrustState.VERIFIED,
                                "Source checked"
                        ),
                        "curator"
                )
        );
    }

    @Test
    void inheritsDocumentSourceWhenReviewerConfirmsPageTrust() {
        SourceReference documentReference = entity(new SourceReference(), 21L);
        Document document = entity(new Document(), 7L);
        document.setDefaultSourceReference(documentReference);
        document.setProvenanceStatus(ProvenanceStatus.KNOWN_SOURCE);

        DocumentPage page = page(11L);
        page.setDocument(document);
        List<DocumentPageProvenanceEvent> events = new ArrayList<>();

        var result = service(
                Map.of(11L, page),
                Map.of(21L, documentReference),
                events
        ).changeTrust(
                11L,
                new PageProvenanceTrustChangeCommand(
                        ProvenanceTrustState.TRUSTED,
                        "Reviewer approval"
                ),
                "curator"
        );

        assertSame(documentReference, page.getSourceReference());
        assertEquals(ProvenanceStatus.KNOWN_SOURCE, page.getProvenanceStatus());
        assertEquals(ProvenanceTrustState.TRUSTED, page.getProvenanceTrustState());
        assertNull(result.previousSourceReferenceId());
        assertEquals(21L, result.newSourceReferenceId());
        assertEquals(1, events.size());
    }

    @Test
    void mergesEvidenceIntoCanonicalPageWithoutChangingItsIdentity() {
        DocumentPage evidence = page(11L);
        DocumentPage canonical = page(12L);
        SourceReference canonicalSource = entity(new SourceReference(), 22L);
        canonical.setSourceReference(canonicalSource);
        canonical.setProvenanceStatus(ProvenanceStatus.KNOWN_SOURCE);
        canonical.setProvenanceTrustState(ProvenanceTrustState.VERIFIED);
        List<DocumentPageProvenanceEvent> events = new ArrayList<>();

        var result = service(
                Map.of(11L, evidence, 12L, canonical),
                Map.of(22L, canonicalSource),
                events
        ).mergeIntoCanonicalPage(
                11L,
                new CanonicalPageLinkCommand(
                        12L,
                        "Capture represents the canonical printed page"
                ),
                "curator"
        );

        assertEquals(11L, evidence.getId());
        assertEquals(EvidenceState.MERGED, evidence.getEvidenceState());
        assertSame(canonical, evidence.getCanonicalDocumentPage());
        assertSame(canonicalSource, evidence.getSourceReference());
        assertEquals(ProvenanceStatus.KNOWN_SOURCE, evidence.getProvenanceStatus());
        assertEquals(ProvenanceTrustState.VERIFIED, evidence.getProvenanceTrustState());
        assertEquals(ProvenanceEventType.MERGED, result.eventType());
        assertNull(result.previousCanonicalDocumentPageId());
        assertEquals(12L, result.newCanonicalDocumentPageId());
        assertEquals(ProvenanceStatus.UNKNOWN_SOURCE, result.previousProvenanceStatus());
        assertEquals(ProvenanceStatus.KNOWN_SOURCE, result.newProvenanceStatus());
    }

    @Test
    void reversesCanonicalLinkButPreservesReviewedSourceProvenance() {
        DocumentPage evidence = page(11L);
        DocumentPage canonical = page(12L);
        SourceReference sourceReference = entity(new SourceReference(), 22L);
        evidence.setCanonicalDocumentPage(canonical);
        evidence.setEvidenceState(EvidenceState.MERGED);
        evidence.setSourceReference(sourceReference);
        evidence.setProvenanceStatus(ProvenanceStatus.KNOWN_SOURCE);
        evidence.setProvenanceTrustState(ProvenanceTrustState.TRUSTED);

        var result = service(
                Map.of(11L, evidence, 12L, canonical),
                Map.of(22L, sourceReference),
                new ArrayList<>()
        ).reverseCanonicalLink(
                11L,
                "The captures are different physical pages",
                "curator"
        );

        assertNull(evidence.getCanonicalDocumentPage());
        assertEquals(EvidenceState.ACTIVE, evidence.getEvidenceState());
        assertSame(sourceReference, evidence.getSourceReference());
        assertEquals(ProvenanceStatus.KNOWN_SOURCE, evidence.getProvenanceStatus());
        assertEquals(ProvenanceTrustState.TRUSTED, evidence.getProvenanceTrustState());
        assertEquals(ProvenanceEventType.LINK_REVERSED, result.eventType());
        assertEquals(12L, result.previousCanonicalDocumentPageId());
        assertNull(result.newCanonicalDocumentPageId());
    }

    @Test
    void rejectsCanonicalSelfReference() {
        DocumentPage page = page(11L);

        assertThrows(
                InvalidDocumentPageProvenanceTransitionException.class,
                () -> service(
                        Map.of(11L, page),
                        Map.of(),
                        new ArrayList<>()
                ).linkCanonicalPage(
                        11L,
                        new CanonicalPageLinkCommand(11L, "Duplicate evidence"),
                        "curator"
                )
        );
    }

    private DocumentPageProvenanceService service(
            Map<Long, DocumentPage> pages,
            Map<Long, SourceReference> sourceReferences,
            List<DocumentPageProvenanceEvent> events
    ) {
        Map<Long, DocumentPage> savedPages = new HashMap<>();

        DocumentPageRepository pageRepository = proxy(
                DocumentPageRepository.class,
                (ignored, method, arguments) -> switch (method.getName()) {
                    case "findByIdForUpdate" -> Optional.ofNullable(
                            pages.get((Long) arguments[0])
                    );
                    case "save" -> {
                        DocumentPage page = (DocumentPage) arguments[0];
                        savedPages.put(page.getId(), page);
                        yield page;
                    }
                    default -> throw new AssertionError(
                            "Unexpected page call: " + method.getName()
                    );
                }
        );
        SourceReferenceRepository sourceRepository = proxy(
                SourceReferenceRepository.class,
                (ignored, method, arguments) -> {
                    if(method.getName().equals("findById"))
                        return Optional.ofNullable(
                                sourceReferences.get((Long) arguments[0])
                        );

                    throw new AssertionError(
                            "Unexpected source-reference call: " + method.getName()
                    );
                }
        );
        DocumentPageProvenanceEventRepository eventRepository = proxy(
                DocumentPageProvenanceEventRepository.class,
                (ignored, method, arguments) -> {
                    if(method.getName().equals("saveAndFlush")) {
                        DocumentPageProvenanceEvent event =
                                (DocumentPageProvenanceEvent) arguments[0];
                        EntityTestUtils.setId(event, (long) events.size() + 31L);
                        events.add(event);
                        return event;
                    }

                    throw new AssertionError(
                            "Unexpected provenance-event call: " + method.getName()
                    );
                }
        );

        return new DocumentPageProvenanceService(
                pageRepository,
                sourceRepository,
                eventRepository,
                new DocumentHistoryMapper(),
                fmi.ethnowear.support.ManagementEventTestSupport.events(),
                org.mockito.Mockito.mock(
                        fmi.ethnowear.application.service.document.review.DocumentPageChunkInvalidator.class
                )
        );
    }

    private DocumentPage page(Long id) {
        DocumentPage page = entity(new DocumentPage(), id);
        page.setEvidenceState(EvidenceState.ACTIVE);
        page.setProvenanceStatus(ProvenanceStatus.UNKNOWN_SOURCE);
        page.setProvenanceTrustState(ProvenanceTrustState.UNKNOWN);
        return page;
    }

    private <T extends fmi.ethnowear.persistence.jpa.entity.AppendOnlyEntity> T entity(
            T entity,
            Long id
    ) {
        EntityTestUtils.setId(entity, id);
        return entity;
    }
}
