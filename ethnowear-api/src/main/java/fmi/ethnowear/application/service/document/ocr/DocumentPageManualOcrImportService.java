package fmi.ethnowear.application.service.document.ocr;

import fmi.ethnowear.application.dto.document.command.ocr.ManualOcrImportCommand;
import fmi.ethnowear.application.dto.document.command.ocr.OcrResultImportCommand;
import fmi.ethnowear.application.dto.document.query.history.DocumentPageOcrResultDetails;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DocumentPageManualOcrImportService {

    private static final String MANUAL_IMPORT_ENGINE = "MANUAL_IMPORT";
    private static final String DEFAULT_OCR_LANGUAGE = "bg";

    private final DocumentPageOcrImportService ocrImportService;
    private final Validator validator;

    @Transactional
    public DocumentPageOcrResultDetails importResult(
            Long pageId,
            ManualOcrImportCommand command
    ) {
        validate(command);

        OcrResultImportCommand internalCommand =
                new OcrResultImportCommand(
                        command.documentPageMediaId(),
                        null,
                        command.rawText(),
                        MANUAL_IMPORT_ENGINE,
                        null,
                        DEFAULT_OCR_LANGUAGE,
                        null,
                        null,
                        null
                );

        return ocrImportService.importResult(pageId, internalCommand);
    }

    private void validate(ManualOcrImportCommand command) {
        if (command == null)
            throw new IllegalArgumentException("Manual OCR import command is required");

        var violation = validator.validate(command);

        if(!violation.isEmpty())
            throw new ConstraintViolationException(violation);
    }
}
