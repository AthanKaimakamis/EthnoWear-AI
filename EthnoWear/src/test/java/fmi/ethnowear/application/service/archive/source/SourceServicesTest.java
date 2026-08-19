package fmi.ethnowear.application.service.archive.source;

import fmi.ethnowear.testutil.EntityTestUtils;

import fmi.ethnowear.application.dto.archive.source.SourceReferenceDetails;
import fmi.ethnowear.application.dto.archive.source.SourceReferenceWriteDto;
import fmi.ethnowear.application.dto.archive.source.SourceWriteDto;
import fmi.ethnowear.domain.model.archive.SourceType;
import fmi.ethnowear.application.exception.ResourceInUseException;
import fmi.ethnowear.persistence.jpa.entity.Source;
import fmi.ethnowear.persistence.jpa.entity.SourceReference;
import fmi.ethnowear.persistence.jpa.repository.SourceReferenceRepository;
import fmi.ethnowear.persistence.jpa.repository.SourceRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static fmi.ethnowear.support.RepositoryTestProxies.proxy;
import static fmi.ethnowear.support.RepositoryTestProxies.rejecting;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SourceServicesTest {

    @Test
    void rejectsBlankSourceTitle() {
        SourceService service = new SourceService(
                rejecting(SourceRepository.class),
                new SourceMapper(),
                new SourceUsageChecker(null)
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.create(sourceInput(" "))
        );

        assertEquals("Source title is required", exception.getMessage());
    }

    @Test
    void blocksDeletionOfReferencedSource() {
        Source source = new Source();
        EntityTestUtils.setId(source, 1L);
        SourceRepository sourceRepository = proxy(SourceRepository.class, (ignored, method, arguments) -> {
            if(method.getName().equals("findById"))
                return Optional.of(source);

            throw new AssertionError("Unexpected repository call: " + method.getName());
        });
        SourceReferenceRepository referenceRepository = proxy(
                SourceReferenceRepository.class,
                (ignored, method, arguments) -> true
        );
        SourceService service = new SourceService(
                sourceRepository,
                new SourceMapper(),
                new SourceUsageChecker(referenceRepository)
        );

        assertThrows(ResourceInUseException.class, () -> service.delete(1L));
    }

    @Test
    void rejectsInvertedSourceReferencePages() {
        SourceReferenceService service = new SourceReferenceService(
                rejecting(SourceReferenceRepository.class),
                rejecting(SourceRepository.class),
                new SourceReferenceMapper(),
                new SourceReferenceUsageChecker(null, null, null, null)
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.create(referenceInput(20, 10))
        );

        assertEquals("Page from cannot be greater than page to", exception.getMessage());
    }

    @Test
    void createsSourceReferenceForExistingSource() {
        Source source = new Source();
        EntityTestUtils.setId(source, 2L);
        SourceRepository sourceRepository = proxy(
                SourceRepository.class,
                (ignored, method, arguments) -> Optional.of(source)
        );
        SourceReferenceRepository referenceRepository = proxy(
                SourceReferenceRepository.class,
                (ignored, method, arguments) -> (SourceReference) arguments[0]
        );
        SourceReferenceService service = new SourceReferenceService(
                referenceRepository,
                sourceRepository,
                new SourceReferenceMapper(),
                new SourceReferenceUsageChecker(null, null, null, null)
        );

        SourceReferenceDetails details = service.create(referenceInput(10, 12));

        assertEquals(2L, details.sourceId());
        assertEquals(10, details.pageFrom());
        assertEquals(12, details.pageTo());
    }

    private SourceWriteDto sourceInput(String title) {
        return new SourceWriteDto(
                title,
                null,
                null,
                null,
                SourceType.BOOK,
                "bg",
                null,
                null,
                null,
                null,
                true
        );
    }

    private SourceReferenceWriteDto referenceInput(Integer pageFrom, Integer pageTo) {
        return new SourceReferenceWriteDto(
                2L,
                "Test chapter",
                pageFrom,
                pageTo,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }
}
