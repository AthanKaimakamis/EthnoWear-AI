package fmi.ethnowear.application.service.archive.media;

import fmi.ethnowear.application.dto.archive.media.MediaAssetDetails;
import fmi.ethnowear.application.dto.archive.media.MediaUploadRequest;
import fmi.ethnowear.application.exception.ResourceNotFoundException;
import fmi.ethnowear.config.MediaStorageProperties;
import fmi.ethnowear.domain.model.archive.MediaType;
import fmi.ethnowear.persistence.jpa.entity.MediaAsset;
import fmi.ethnowear.persistence.jpa.entity.SourceReference;
import fmi.ethnowear.persistence.jpa.repository.MediaAssetRepository;
import fmi.ethnowear.persistence.jpa.repository.SourceReferenceRepository;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

import static fmi.ethnowear.util.IdentifierUtils.requireId;

@Service
@RequiredArgsConstructor
public class MediaUploadService {
    private static final Set<String> CATEGORIES = Set.of("archive", "documents", "entities");
    private final MediaAssetRepository repository;
    private final SourceReferenceRepository sourceReferenceRepository;
    private final MediaAssetMapper mapper;
    private final MediaPathResolver paths;
    private final MediaStorageProperties properties;

    @Transactional
    public MediaAssetDetails upload(MultipartFile file, MediaUploadRequest request) {
        validate(file, request);
        String mime = Objects.requireNonNull(file.getContentType()).toLowerCase(Locale.ROOT);
        String extension = extensionFor(mime);
        String category = request.category() == null || request.category().isBlank()
                ? categoryFor(request.mediaType()) : request.category().trim().toLowerCase(Locale.ROOT);
        if (!CATEGORIES.contains(category))
            throw new IllegalArgumentException("Unsupported media category");

        String relative = category + "/" + UUID.randomUUID() + extension;

        Path target;
        try {
            target = paths.resolveForWrite(relative);
        } catch (IOException ex) {
            throw new IllegalStateException("Could not prepare media upload destination", ex);
        }

        String thumbnailRelative = null;
        try {
            try (InputStream input = file.getInputStream()) {
                Files.copy(input, target);
            }
            validateSignature(target, mime);
            ImageInfo image = imageInfo(target, mime);
            if (image != null)
                thumbnailRelative = createThumbnail(target);

            MediaAsset asset = new MediaAsset();
            asset.setSourceReference(reference(request.sourceReferenceId()));
            asset.setFileName(safeOriginalName(file.getOriginalFilename()));
            asset.setFilePath(relative);
            asset.setMimeType(mime);
            asset.setMediaType(request.mediaType());
            asset.setWidth(image == null ? null : image.width());
            asset.setHeight(image == null ? null : image.height());
            asset.setSizeBytes(Files.size(target));
            asset.setChecksum(sha256(target));
            asset.setDescription(request.description());
            asset.setThumbnailPath(thumbnailRelative);
            return mapper.toDetails(repository.save(asset));
        } catch (RuntimeException | IOException ex) {
            deleteQuietly(target);

            if (thumbnailRelative != null)
                deleteQuietly(paths.resolve(thumbnailRelative));

            if (ex instanceof RuntimeException runtime)
                throw runtime;

            throw new IllegalStateException("Could not store media file", ex);
        }
    }

    private void validate(MultipartFile file, MediaUploadRequest request) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("A non-empty file is required");
        if (request == null || request.mediaType() == null)
            throw new IllegalArgumentException("Media type is required");
        if (file.getSize() > properties.getMaxFileSize().toBytes())
            throw new IllegalArgumentException("Media file exceeds the maximum size");
        String mime = file.getContentType();
        if (mime == null || !properties.getAllowedContentTypes().contains(mime.toLowerCase(Locale.ROOT)))
            throw new IllegalArgumentException("Unsupported media content type");
    }

    private SourceReference reference(Long id) {
        if (id == null) return null;

        requireId(id, "Source reference");

        return sourceReferenceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Source reference", id));
    }

    private void validateSignature(Path file, String mime) throws IOException {
        byte[] header = new byte[12];
        int count;
        try (InputStream in = Files.newInputStream(file)) {
            count = in.read(header);
        }
        boolean valid = switch (mime) {
            case "image/jpeg" ->
                    count >= 3 && header[0] == (byte) 0xff && header[1] == (byte) 0xd8 && header[2] == (byte) 0xff;
            case "image/png" ->
                    count >= 8 && header[0] == (byte) 0x89 && header[1] == 'P' && header[2] == 'N' && header[3] == 'G';
            case "image/gif" -> count >= 6 && header[0] == 'G' && header[1] == 'I' && header[2] == 'F';
            case "image/webp" ->
                    count >= 12 && header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P';
            case "application/pdf" ->
                    count >= 5 && header[0] == '%' && header[1] == 'P' && header[2] == 'D' && header[3] == 'F' && header[4] == '-';
            default -> false;
        };
        if (!valid) throw new IllegalArgumentException("File content does not match its declared content type");
    }

    private @Nullable ImageInfo imageInfo(Path path, @NonNull String mime) throws IOException {
        if (!mime.startsWith("image/") || mime.equals("image/webp")) return null;
        BufferedImage image = ImageIO.read(path.toFile());
        if (image == null) throw new IllegalArgumentException("Invalid image file");
        return new ImageInfo(image.getWidth(), image.getHeight());
    }

    private @Nullable String createThumbnail(@NonNull Path source) throws IOException {
        BufferedImage original = ImageIO.read(source.toFile());
        if (original == null) return null;
        int width = Math.min(480, original.getWidth());
        int height = Math.max(1, original.getHeight() * width / original.getWidth());
        BufferedImage thumb = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = thumb.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.drawImage(original, 0, 0, width, height, null);
        graphics.dispose();
        String relative = "thumbnails/" + UUID.randomUUID() + ".jpg";

        Path target;
        try {
            target = paths.resolveForWrite(relative);
        } catch (IOException ex) {
            throw new IllegalStateException("Could not prepare media upload destination", ex);
        }

        Files.createDirectories(target.getParent());
        if (!ImageIO.write(thumb, "jpg", target.toFile()))
            throw new IOException("No JPEG thumbnail writer is available");
        return relative;
    }

    private String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (DigestInputStream input = new DigestInputStream(Files.newInputStream(path), digest)) {
                input.transferTo(java.io.OutputStream.nullOutputStream());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private @NonNull String safeOriginalName(String name) {
        if (name == null || name.isBlank()) return "upload";
        return Path.of(name.replace('\\', '/')).getFileName().toString();
    }

    @Contract(pure = true)
    private @NonNull String categoryFor(MediaType type) {
        return type == MediaType.PDF ? "documents" : "archive";
    }

    @Contract(pure = true)
    private @NonNull String extensionFor(@NonNull String mime) {
        return switch (mime) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/gif" -> ".gif";
            case "image/webp" -> ".webp";
            case "application/pdf" -> ".pdf";
            default -> "";
        };
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }

    private record ImageInfo(int width, int height) {
    }
}
