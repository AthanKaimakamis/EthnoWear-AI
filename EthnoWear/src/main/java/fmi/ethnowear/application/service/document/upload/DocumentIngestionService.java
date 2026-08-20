package fmi.ethnowear.application.service.document.upload;

import fmi.ethnowear.application.dto.document.command.upload.*;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class DocumentIngestionService {

    private final PdfDocumentUploadService pdfUploadService;
    private final StandaloneCaptureUploadService standaloneCaptureUploadService;
    private final MissingPageUploadService missingPageUploadService;
    private final ReplacementRenditionUploadService replacementRenditionUploadService;

    public @NonNull DocumentUploadDetails uploadPdf(
            @NonNull PdfDocumentUploadCommand command,
            @NonNull MultipartFile file) {
        return pdfUploadService.upload(command, file);
    }

    public @NonNull DocumentUploadDetails uploadStandaloneCapture(
            @NonNull StandaloneCaptureUploadCommand command,
            @NonNull MultipartFile file
    ) {
        return standaloneCaptureUploadService.upload(command, file);
    }

    public @NonNull DocumentUploadDetails addMissingPage(
            Long documentId,
            @NonNull MissingPageUploadCommand command,
            @NonNull MultipartFile file
    ) {
        return missingPageUploadService.upload(documentId, command, file);
    }

    public @NonNull DocumentUploadDetails addReplacementRendition(
            Long documentId,
            Long pageId,
            @NonNull ReplacementRenditionUploadCommand command,
            @NonNull MultipartFile file
    ) {
        return replacementRenditionUploadService.upload(documentId, pageId, command, file);
    }


}
