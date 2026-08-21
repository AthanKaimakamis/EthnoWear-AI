package fmi.ethnowear.application.service.archive.media.storage;

import fmi.ethnowear.application.dto.archive.media.MediaAssetDetails;
import fmi.ethnowear.application.dto.archive.media.MediaUploadRequest;
import fmi.ethnowear.application.exception.ResourceNotFoundException;
import fmi.ethnowear.application.service.archive.media.asset.MediaAssetMapper;
import fmi.ethnowear.config.MediaStorageProperties;
import fmi.ethnowear.domain.model.archive.MediaType;
import fmi.ethnowear.persistence.jpa.entity.MediaAsset;
import fmi.ethnowear.persistence.jpa.entity.SourceReference;
import fmi.ethnowear.persistence.jpa.repository.MediaAssetRepository;
import fmi.ethnowear.persistence.jpa.repository.SourceReferenceRepository;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;
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

    private static final Set<String> CATEGORIES = Set.of(
            "archive",
            "documents",
            "entities"
    );

    private final MediaAssetRepository repository;
    private final SourceReferenceRepository sourceReferenceRepository;
    private final MediaAssetMapper mapper;
    private final MediaPathResolver paths;
    private final MediaStorageProperties properties;
    private final MediaContentValidator contentValidator;
    private final MediaFileCompensation compensation;
    private final MediaFileHasher fileHasher;

    @Transactional
    public MediaAssetDetails upload(
            MultipartFile file,
            MediaUploadRequest request
    ) {
        validate(file, request);

        String category = request.category() == null
                || request.category().isBlank()
                ? categoryFor(request.mediaType())
                : request.category().trim().toLowerCase(Locale.ROOT);

        if (!CATEGORIES.contains(category))
            throw new IllegalArgumentException("Unsupported media category");

        return store(
                file,
                request,
                MediaUploadDestination.generic(category)
        );
    }

    @Transactional
    public MediaAssetDetails upload(
            MultipartFile file,
            MediaUploadRequest request,
            MediaUploadDestination destination
    ) {
        validate(file, request);

        if (destination == null)
            throw new IllegalArgumentException(
                    "Media upload destination is required"
            );

        return store(file, request, destination);
    }

    private MediaAssetDetails store(
            @NonNull MultipartFile file,
            @NonNull MediaUploadRequest request,
            @NonNull MediaUploadDestination destination
    ) {
        String mimeType = Objects.requireNonNull(file.getContentType())
                .toLowerCase(Locale.ROOT);

        String relativePath = destination.fileDirectory()
                + "/"
                + UUID.randomUUID()
                + extensionFor(mimeType);

        SourceReference sourceReference = reference(
                request.sourceReferenceId()
        );

        List<Path> createdPaths = new ArrayList<>();
        String thumbnailPath = null;

        try {
            Path target = paths.resolveForWrite(relativePath);
            createdPaths.add(target);

            try (InputStream input = file.getInputStream()) {
                Files.copy(input, target);
            }

            MediaFileInspection inspection = contentValidator.inspect(
                    target,
                    mimeType
            );

            if (inspection.width() != null) {
                ThumbnailFile thumbnail = createThumbnail(
                        target,
                        destination.thumbnailDirectory()
                );

                thumbnailPath = thumbnail.relativePath();
                createdPaths.add(thumbnail.path());
            }

            compensation.registerRollbackCleanup(createdPaths);

            MediaAsset asset = new MediaAsset();
            asset.setSourceReference(sourceReference);
            asset.setFileName(safeOriginalName(file.getOriginalFilename()));
            asset.setFilePath(relativePath);
            asset.setMimeType(mimeType);
            asset.setMediaType(request.mediaType());
            asset.setWidth(inspection.width());
            asset.setHeight(inspection.height());
            asset.setSizeBytes(Files.size(target));
            asset.setChecksum(fileHasher.sha256(target));
            asset.setDescription(request.description());
            asset.setThumbnailPath(thumbnailPath);

            return mapper.toDetails(repository.save(asset));
        } catch (RuntimeException | IOException ex) {
            compensation.deleteQuietly(createdPaths);

            if (ex instanceof RuntimeException runtimeException)
                throw runtimeException;

            throw new IllegalStateException("Could not store media file", ex);
        }
    }

    private void validate(MultipartFile file, MediaUploadRequest request) {
        if (file == null || file.isEmpty())
            throw new IllegalArgumentException("A non-empty file is required");

        if (request == null || request.mediaType() == null)
            throw new IllegalArgumentException("Media type is required");

        if (file.getSize() > properties.getMaxFileSize().toBytes())
            throw new IllegalArgumentException("Media file exceeds the maximum size");

        String mime = file.getContentType();

        if (mime == null)
            throw new IllegalArgumentException("Media content type is required");

        String normalizedMime = mime.toLowerCase(Locale.ROOT);

        if (!properties.getAllowedContentTypes().contains(normalizedMime))
            throw new IllegalArgumentException("Unsupported media content type");

        validateMediaType(request.mediaType(), normalizedMime);
    }

    private SourceReference reference(Long id) {
        if (id == null) return null;

        requireId(id, "Source reference");

        return sourceReferenceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Source reference", id));
    }

    @Contract("_, _ -> new")
    private @NonNull ThumbnailFile createThumbnail(@NonNull Path source, String thumbnailDirectory)
            throws IOException {
        BufferedImage original = ImageIO.read(source.toFile());

        if (original == null)
            throw new IllegalArgumentException("Invalid image file");

        int width = Math.min(480, original.getWidth());
        int height = Math.max(
                1,
                original.getHeight() * width / original.getWidth()
        );

        BufferedImage thumbnail = new BufferedImage(
                width,
                height,
                BufferedImage.TYPE_INT_RGB
        );

        Graphics2D graphics = thumbnail.createGraphics();

        try {
            graphics.setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR
            );

            graphics.drawImage(
                    original,
                    0,
                    0,
                    width,
                    height,
                    null
            );
        } finally {
            graphics.dispose();
        }

        String relativePath = thumbnailDirectory
                + "/"
                + UUID.randomUUID()
                + ".jpg";

        Path target = paths.resolveForWrite(relativePath);

        try {
            if (!ImageIO.write(thumbnail, "jpg", target.toFile()))
                throw new IOException(
                        "No JPEG thumbnail writer is available"
                );

            return new ThumbnailFile(relativePath, target);
        } catch (RuntimeException | IOException ex) {
            compensation.deleteQuietly(target);
            throw ex;
        }
    }

    private record ThumbnailFile(
            String relativePath,
            Path path
    ) {
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
            default -> throw new IllegalArgumentException("Unsupported media content type: " + mime);
        };
    }

    private void validateMediaType(@NonNull MediaType mediaType, @NonNull String mimeType) {
        boolean image = mimeType.startsWith("image/");
        boolean pdf = mimeType.equals("application/pdf");

        boolean valid = switch (mediaType) {
            case IMAGE, SCAN, THUMBNAIL -> image;
            case PDF -> pdf;
            case OTHER -> image || pdf;
        };

        if (!valid)
            throw new IllegalArgumentException("Media type does not match file content type");
    }
}
