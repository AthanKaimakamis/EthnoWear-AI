package fmi.ethnowear.application.service.document.metadata;

import fmi.ethnowear.application.dto.document.command.metadata.DocumentPageMetadataUpdateCommand;
import fmi.ethnowear.application.service.document.review.DocumentPageChunkInvalidator;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPage;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageRepository;
import fmi.ethnowear.testutil.EntityTestUtils;
import fmi.ethnowear.util.RowVersionUtils;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static fmi.ethnowear.support.RepositoryTestProxies.proxy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class DocumentPageMetadataServiceTest {

    @Test
    void updatesPrintedPageMetadataAndInvalidatesChunkCitations() {
        DocumentPage page = new DocumentPage();
        EntityTestUtils.setId(page, 11L);
        byte[] rowVersion = new byte[]{1, 2, 3, 4, 5, 6, 7, 8};
        ReflectionTestUtils.setField(page, "rowVersion", rowVersion);
        AtomicReference<DocumentPage> saved = new AtomicReference<>();
        DocumentPageRepository repository = proxy(
                DocumentPageRepository.class,
                (ignored, method, arguments) -> switch (method.getName()) {
                    case "findByIdAndDocument_Id" -> Optional.of(page);
                    case "save" -> {
                        saved.set((DocumentPage) arguments[0]);
                        yield arguments[0];
                    }
                    default -> throw new AssertionError(
                            "Unexpected page call: " + method.getName()
                    );
                }
        );
        DocumentPageChunkInvalidator invalidator = mock(
                DocumentPageChunkInvalidator.class
        );
        var validatorFactory = Validation.buildDefaultValidatorFactory();
        DocumentPageMetadataService service = new DocumentPageMetadataService(
                repository,
                invalidator,
                fmi.ethnowear.support.ManagementEventTestSupport.events(),
                validatorFactory.getValidator()
        );

        service.update(
                7L,
                11L,
                RowVersionUtils.token(rowVersion),
                new DocumentPageMetadataUpdateCommand(
                        "  xv  ",
                        15,
                        "  Preface  "
                )
        );

        assertEquals("xv", saved.get().getPrintedPageNumber());
        assertEquals(15, saved.get().getPrintedPageSort());
        assertEquals("Preface", saved.get().getPageLabel());
        verify(invalidator).invalidate(page);
    }

    @Test
    void rejectsStalePageMetadataUpdate() {
        DocumentPage page = new DocumentPage();
        EntityTestUtils.setId(page, 11L);
        ReflectionTestUtils.setField(
                page,
                "rowVersion",
                new byte[]{1, 2, 3, 4, 5, 6, 7, 8}
        );
        DocumentPageRepository repository = proxy(
                DocumentPageRepository.class,
                (ignored, method, arguments) -> {
                    if(method.getName().equals("findByIdAndDocument_Id"))
                        return Optional.of(page);

                    throw new AssertionError(
                            "Unexpected page call: " + method.getName()
                    );
                }
        );
        var validatorFactory = Validation.buildDefaultValidatorFactory();
        DocumentPageMetadataService service = new DocumentPageMetadataService(
                repository,
                mock(DocumentPageChunkInvalidator.class),
                fmi.ethnowear.support.ManagementEventTestSupport.events(),
                validatorFactory.getValidator()
        );

        assertThrows(
                RowVersionUtils.StaleRowVersionException.class,
                () -> service.update(
                        7L,
                        11L,
                        RowVersionUtils.token(
                                new byte[]{8, 7, 6, 5, 4, 3, 2, 1}
                        ),
                        new DocumentPageMetadataUpdateCommand("15", 15, null)
                )
        );
    }
}
