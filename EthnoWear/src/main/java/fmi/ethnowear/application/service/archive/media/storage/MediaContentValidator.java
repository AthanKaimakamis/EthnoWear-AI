package fmi.ethnowear.application.service.archive.media.storage;

import fmi.ethnowear.application.exception.UnprocessableDocumentEvidenceException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class MediaContentValidator {

    public MediaFileInspection inspect(Path path, String mimeType) throws IOException {
        validateSignature(path, mimeType);

        if("application/pdf".equals(mimeType)) {
            validatePdf(path);
            return MediaFileInspection.withoutDimensions();
        }

        if("image/webp".equals(mimeType))
            return MediaFileInspection.withoutDimensions();

        BufferedImage image = ImageIO.read(path.toFile());

        if(image == null)
            throw new UnprocessableDocumentEvidenceException("Invalid image file");

        return new MediaFileInspection(
                image.getWidth(),
                image.getHeight()
        );
    }

    private void validatePdf(@NonNull Path path) {
        try (var document = Loader.loadPDF(path.toFile())) {
            if(document.isEncrypted())
                throw new UnprocessableDocumentEvidenceException(
                        "Encrypted PDF files are not supported"
                );

            if(document.getNumberOfPages() == 0)
                throw new UnprocessableDocumentEvidenceException(
                        "PDF file must contain at least one page"
                );
        } catch (InvalidPasswordException ex) {
            throw new UnprocessableDocumentEvidenceException(
                    "Encrypted PDF files are not supported",
                    ex
            );
        } catch (IOException ex) {
            throw new UnprocessableDocumentEvidenceException(
                    "Invalid or unsupported PDF file",
                    ex
            );
        }
    }

    private void validateSignature(Path path, String mimeType) throws IOException {
        byte[] header = new byte[12];
        int count;

        try (InputStream input = Files.newInputStream(path)) {
            count = input.read(header);
        }

        boolean valid = switch (mimeType) {
            case "image/jpeg" -> count >= 3
                    && header[0] == (byte) 0xff
                    && header[1] == (byte) 0xd8
                    && header[2] == (byte) 0xff;
            case "image/png" -> count >= 8
                    && header[0] == (byte) 0x89
                    && header[1] == 'P'
                    && header[2] == 'N'
                    && header[3] == 'G';
            case "image/gif" -> count >= 6
                    && header[0] == 'G'
                    && header[1] == 'I'
                    && header[2] == 'F';
            case "image/webp" -> count >= 12
                    && header[0] == 'R'
                    && header[1] == 'I'
                    && header[2] == 'F'
                    && header[8] == 'W'
                    && header[9] == 'E'
                    && header[10] == 'B'
                    && header[11] == 'P';
            case "application/pdf" -> count >= 5
                    && header[0] == '%'
                    && header[1] == 'P'
                    && header[2] == 'D'
                    && header[3] == 'F'
                    && header[4] == '-';
            default -> false;
        };

        if(!valid)
            throw new UnprocessableDocumentEvidenceException(
                    "File content does not match its declared content type"
            );
    }
}