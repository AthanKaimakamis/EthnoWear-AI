package fmi.ethnowear.application.service.document.upload;

import fmi.ethnowear.application.dto.document.command.upload.DocumentBibliographicInput;
import fmi.ethnowear.application.dto.document.command.upload.PageProvenanceInput;
import fmi.ethnowear.application.dto.document.command.upload.StandaloneCaptureUploadCommand;
import fmi.ethnowear.domain.model.document.DocumentPageRenditionType;
import fmi.ethnowear.domain.model.document.DocumentType;
import fmi.ethnowear.domain.model.document.PageKind;
import fmi.ethnowear.domain.model.document.PageRole;
import fmi.ethnowear.domain.model.document.provenance.ProvenanceEventType;
import fmi.ethnowear.domain.model.document.provenance.ProvenanceStatus;
import fmi.ethnowear.domain.model.document.provenance.ProvenanceTrustState;
import fmi.ethnowear.persistence.jpa.entity.MediaAsset;
import fmi.ethnowear.persistence.jpa.entity.SourceReference;
import fmi.ethnowear.persistence.jpa.entity.Source;
import fmi.ethnowear.persistence.jpa.entity.document.Document;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPage;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageProvenanceEventRepository;
import org.junit.jupiter.api.Test;

import static fmi.ethnowear.support.RepositoryTestProxies.proxy;
import static org.junit.jupiter.api.Assertions.*;

class DocumentUploadFactoriesTest {

    @Test
    void storesTheDefaultDocumentSourceReference() {
        Source source = new Source();
        SourceReference sourceReference = new SourceReference();

        Document document = new DocumentCreationFactory().create(
                standaloneCommand().metadata(),
                DocumentType.SCANNED_BOOK,
                ProvenanceStatus.KNOWN_SOURCE,
                ProvenanceTrustState.VERIFIED,
                source,
                sourceReference
        );

        assertSame(source, document.getSource());
        assertSame(sourceReference, document.getDefaultSourceReference());
    }

    @Test
    void createsStableStandalonePageWithExplicitProvenance() {
        Document document = new Document();
        SourceReference sourceReference = new SourceReference();
        StandaloneCaptureUploadCommand command = standaloneCommand();

        DocumentPage page = new DocumentPageFactory().createStandalone(
                document,
                sourceReference,
                command
        );

        assertSame(document, page.getDocument());
        assertSame(sourceReference, page.getSourceReference());
        assertEquals(PageKind.STANDALONE_IMAGE, page.getPageKind());
        assertEquals(PageRole.NORMAL, page.getPageRole());
        assertEquals(1, page.getPageSequence());
        assertNull(page.getPdfPageIndex());
        assertEquals(ProvenanceStatus.PARTIAL_SOURCE, page.getProvenanceStatus());
        assertEquals(ProvenanceTrustState.PARTIAL, page.getProvenanceTrustState());
        assertEquals("Curator note", page.getProvenanceNote());
        assertEquals("curator", page.getProvenanceReviewedBy());
    }

    @Test
    void createsOriginalRenditionFromServerManagedMediaMetadata() {
        DocumentPage page = new DocumentPage();
        MediaAsset media = new MediaAsset();
        media.setWidth(1200);
        media.setHeight(800);
        media.setChecksum("abc123");

        var rendition = new DocumentPageMediaFactory().createOriginal(
                page,
                media,
                "Original capture"
        );

        assertSame(page, rendition.getDocumentPage());
        assertSame(media, rendition.getMediaAsset());
        assertEquals(DocumentPageRenditionType.ORIGINAL_UPLOAD, rendition.getRenditionType());
        assertTrue(rendition.isOriginal());
        assertTrue(rendition.isPreferredOcrInput());
        assertEquals(0, rendition.getDisplayOrder());
        assertEquals(1200, rendition.getWidth());
        assertEquals(800, rendition.getHeight());
        assertEquals("abc123", rendition.getRenditionHash());
    }

    @Test
    void recordsInitialProvenanceAsAppendOnlyHistory() {
        PageProvenanceInput provenance = provenance();
        DocumentPage page = new DocumentPage();
        SourceReference sourceReference = new SourceReference();

        DocumentPageProvenanceEventRepository repository = proxy(
                DocumentPageProvenanceEventRepository.class,
                (ignored, method, arguments) -> {
                    assertEquals("save", method.getName());
                    return arguments[0];
                }
        );

        var event = new DocumentPageProvenanceRecorder(repository)
                .recordInitial(page, sourceReference, provenance);

        assertSame(page, event.getDocumentPage());
        assertEquals(ProvenanceEventType.SOURCE_IDENTIFIED, event.getEventType());
        assertNull(event.getPreviousSourceReference());
        assertSame(sourceReference, event.getNewSourceReference());
        assertNull(event.getPreviousProvenanceStatus());
        assertEquals(ProvenanceStatus.PARTIAL_SOURCE, event.getNewProvenanceStatus());
        assertEquals(ProvenanceTrustState.PARTIAL, event.getNewTrustState());
        assertEquals("curator", event.getReviewedBy());
        assertEquals("Initial provenance assessment", event.getReason());
    }

    private StandaloneCaptureUploadCommand standaloneCommand() {
        return new StandaloneCaptureUploadCommand(
                new DocumentBibliographicInput(
                        4L,
                        null,
                        "Standalone capture",
                        null,
                        null,
                        null,
                        "bg",
                        null
                ),
                provenance(),
                "15",
                15,
                "Page 15",
                true,
                "Uploaded photograph"
        );
    }

    private PageProvenanceInput provenance() {
        return new PageProvenanceInput(
                9L,
                ProvenanceStatus.PARTIAL_SOURCE,
                ProvenanceTrustState.PARTIAL,
                "Curator note",
                "curator",
                "Initial provenance assessment"
        );
    }
}
