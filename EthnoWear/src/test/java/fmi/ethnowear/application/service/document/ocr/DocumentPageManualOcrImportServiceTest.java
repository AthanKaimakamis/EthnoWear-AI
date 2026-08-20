package fmi.ethnowear.application.service.document.ocr;

import fmi.ethnowear.application.dto.document.command.ocr.ManualOcrImportCommand;
import fmi.ethnowear.application.dto.document.command.ocr.OcrResultImportCommand;
import fmi.ethnowear.application.dto.document.query.history.DocumentPageOcrResultDetails;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class DocumentPageManualOcrImportServiceTest {

    @Test
    void mapsRestrictedInputToServerOwnedInternalMetadata() {
        AtomicReference<Long> capturedPageId = new AtomicReference<>();
        AtomicReference<OcrResultImportCommand> capturedCommand = new AtomicReference<>();
        DocumentPageOcrImportService internalService = capturingService(
                capturedPageId,
                capturedCommand
        );

        try (var validatorFactory = Validation.buildDefaultValidatorFactory()) {
            DocumentPageManualOcrImportService service =
                    new DocumentPageManualOcrImportService(
                            internalService,
                            validatorFactory.getValidator()
                    );

            service.importResult(
                    11L,
                    new ManualOcrImportCommand(21L, "manually supplied text")
            );
        }

        OcrResultImportCommand internalCommand = capturedCommand.get();
        assertEquals(11L, capturedPageId.get());
        assertEquals(21L, internalCommand.documentPageMediaId());
        assertEquals("manually supplied text", internalCommand.rawText());
        assertEquals("MANUAL_IMPORT", internalCommand.ocrEngine());
        assertEquals("bg", internalCommand.ocrLanguage());
        assertNull(internalCommand.processingJobId());
        assertNull(internalCommand.ocrEngineVersion());
        assertNull(internalCommand.ocrConfidence());
        assertNull(internalCommand.parametersJson());
        assertNull(internalCommand.structuredOutputJson());
    }

    @Test
    void exposesOnlyMediaIdentityAndRawTextToTheController() {
        List<String> components = Arrays.stream(
                        ManualOcrImportCommand.class.getRecordComponents()
                )
                .map(RecordComponent::getName)
                .toList();

        assertEquals(
                List.of("documentPageMediaId", "rawText"),
                components
        );
    }

    @Test
    void rejectsInvalidManualCommandBeforeCallingInternalService() {
        AtomicReference<OcrResultImportCommand> capturedCommand =
                new AtomicReference<>();

        try (var validatorFactory = Validation.buildDefaultValidatorFactory()) {
            DocumentPageManualOcrImportService service =
                    new DocumentPageManualOcrImportService(
                            capturingService(
                                    new AtomicReference<>(),
                                    capturedCommand
                            ),
                            validatorFactory.getValidator()
                    );

            assertThrows(
                    ConstraintViolationException.class,
                    () -> service.importResult(
                            11L,
                            new ManualOcrImportCommand(null, "text")
                    )
            );
        }

        assertNull(capturedCommand.get());
    }

    private DocumentPageOcrImportService capturingService(
            AtomicReference<Long> pageId,
            AtomicReference<OcrResultImportCommand> command
    ) {
        return new DocumentPageOcrImportService(
                null,
                null,
                null,
                null,
                null,
                null
        ) {
            @Override
            public DocumentPageOcrResultDetails importResult(
                    Long capturedPageId,
                    OcrResultImportCommand capturedCommand
            ) {
                pageId.set(capturedPageId);
                command.set(capturedCommand);
                return null;
            }
        };
    }
}
