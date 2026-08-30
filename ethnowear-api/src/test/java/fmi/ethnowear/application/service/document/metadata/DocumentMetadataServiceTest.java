package fmi.ethnowear.application.service.document.metadata;

import fmi.ethnowear.application.dto.document.command.metadata.DocumentMetadataUpdateCommand;
import fmi.ethnowear.domain.model.document.DocumentType;
import fmi.ethnowear.domain.model.document.indexing.IndexingState;
import fmi.ethnowear.domain.model.document.processing.ProcessingState;
import fmi.ethnowear.domain.model.document.provenance.ProvenanceStatus;
import fmi.ethnowear.persistence.jpa.entity.MediaAsset;
import fmi.ethnowear.persistence.jpa.entity.Source;
import fmi.ethnowear.persistence.jpa.entity.document.Document;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentRepository;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static fmi.ethnowear.support.RepositoryTestProxies.proxy;
import static org.junit.jupiter.api.Assertions.*;

class DocumentMetadataServiceTest {

    @Test
    void updatesOnlyCuratorOwnedMetadata() {
        Document document = new Document();
        Source source = new Source();
        MediaAsset original = new MediaAsset();
        document.setSource(source);
        document.setOriginalMediaAsset(original);
        document.setDocumentType(DocumentType.SCANNED_BOOK);
        document.setProvenanceStatus(ProvenanceStatus.KNOWN_SOURCE);
        document.setProcessingState(ProcessingState.COMPLETED);
        document.setIndexingState(IndexingState.INDEXED);
        AtomicReference<Document> saved = new AtomicReference<>();
        DocumentRepository repository = proxy(
                DocumentRepository.class,
                (ignored, method, arguments) -> switch (method.getName()) {
                    case "findById" -> Optional.of(document);
                    case "save" -> {
                        saved.set((Document) arguments[0]);
                        yield arguments[0];
                    }
                    default -> throw new AssertionError("Unexpected document call: " + method.getName());
                }
        );
        var validatorFactory = Validation.buildDefaultValidatorFactory();
        DocumentMetadataService service = new DocumentMetadataService(
                repository,
                validatorFactory.getValidator()
        );

        service.update(
                7L,
                new DocumentMetadataUpdateCommand(
                        " Updated title ",
                        " ",
                        " Publisher ",
                        1974,
                        " bg ",
                        " Notes "
                )
        );

        assertEquals("Updated title", saved.get().getTitle());
        assertNull(saved.get().getAuthor());
        assertEquals("Publisher", saved.get().getPublisher());
        assertEquals(1974, saved.get().getPublicationYear());
        assertEquals("bg", saved.get().getLanguage());
        assertEquals("Notes", saved.get().getNotes());
        assertSame(source, saved.get().getSource());
        assertSame(original, saved.get().getOriginalMediaAsset());
        assertEquals(DocumentType.SCANNED_BOOK, saved.get().getDocumentType());
        assertEquals(ProvenanceStatus.KNOWN_SOURCE, saved.get().getProvenanceStatus());
        assertEquals(ProcessingState.COMPLETED, saved.get().getProcessingState());
        assertEquals(IndexingState.INDEXED, saved.get().getIndexingState());
    }
}
